# Kafka Consumer Offset과 Rebalance

> **관련 문서**
> - [Kafka 심화](./03.카프카_심화.md)
> - [Kafka Exactly-Once Semantics](./04.카프카_exactly_once.md)
> - [DLQ 설계 패턴](../system-design/dlq_design_patterns.md)

---

## 1. Consumer Group과 Partition 할당

Kafka Consumer Group은 여러 Consumer가 하나의 Topic을 나누어 처리하는 방식이다.
하나의 Partition은 같은 Group 안에서 동시에 하나의 Consumer에게만 할당된다.

```
Topic orders: P0 P1 P2 P3

Group payment-consumer
  C1 -> P0, P1
  C2 -> P2, P3
```

Consumer 수가 Partition 수보다 많으면 일부 Consumer는 유휴 상태가 된다.

```
Partition 4개, Consumer 6개
-> 4개 Consumer만 할당, 2개는 idle
```

병렬 처리 최대치는 Partition 수가 상한이다.

---

## 2. Offset Commit

Offset은 Consumer가 어디까지 읽었는지 나타내는 위치다.
Consumer Group별 offset은 내부 topic인 `__consumer_offsets`에 저장된다.

### Auto Commit

```properties
enable.auto.commit=true
auto.commit.interval.ms=5000
```

장점:
- 설정이 단순하다.

단점:
- 메시지 처리 완료 전 offset이 commit될 수 있다.
- 처리 중 장애가 나면 메시지가 유실된 것처럼 보일 수 있다.
- 처리 완료 후 다음 auto commit 전에 재시작하면 같은 메시지가 다시 처리되어 중복이 발생할 수 있다.

### Manual Commit

```java
consumer.commitSync();
```

처리 성공 후 offset을 commit한다.

```java
while (true) {
    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
    for (ConsumerRecord<String, String> record : records) {
        process(record);
    }
    consumer.commitSync();
}
```

이 방식은 유실 가능성을 줄이지만, commit 전 장애가 나면 같은 메시지를 다시 처리할 수 있다.
따라서 Consumer 로직은 멱등해야 한다.

---

## 3. 처리 순서와 Commit 위치

### 잘못된 순서

```
1. offset commit
2. DB 저장
3. 장애 발생
```

이 경우 Kafka는 이미 처리된 것으로 판단하지만 DB 저장은 안 됐다.
메시지 유실과 동일한 결과가 된다.

### 권장 순서

```
1. 메시지 처리
2. DB 저장 또는 외부 side-effect 완료
3. offset commit
```

장애 시 중복 처리가 발생할 수 있으므로, DB에는 `event_id`, `message_id`, `idempotency_key` 같은 유니크 키를 둔다.

```sql
CREATE TABLE processed_event (
    event_id VARCHAR(100) PRIMARY KEY,
    processed_at TIMESTAMP NOT NULL
);
```

---

## 4. Rebalance

Rebalance는 Consumer Group의 Partition 할당이 다시 계산되는 과정이다.

발생 조건:
- Consumer 추가
- Consumer 종료
- `session.timeout.ms` 초과: heartbeat가 도착하지 않아 Consumer가 죽었다고 판단
- `max.poll.interval.ms` 초과: Consumer가 살아 있어 heartbeat는 보내지만, 다음 `poll()`이 너무 늦어 정상 처리 불가로 판단
- Topic Partition 수 변경

Rebalance 중에는 일부 Partition 소비가 일시 중단된다.
처리 시간이 길거나 Consumer가 자주 재시작되면 Lag가 급증할 수 있다.

### 주요 설정

| 설정 | 의미 |
|---|---|
| `session.timeout.ms` | heartbeat가 없을 때 Consumer 사망 판단 시간 |
| `heartbeat.interval.ms` | heartbeat 전송 주기 |
| `max.poll.interval.ms` | poll 간 최대 허용 시간 |
| `max.poll.records` | 한 번에 가져올 최대 레코드 수 |

`heartbeat.interval.ms`는 일반적으로 `session.timeout.ms`의 1/3 이하로 설정한다.
그래야 session timeout 안에 여러 번 heartbeat를 보낼 수 있어 일시적인 네트워크 지연에 덜 민감하다.

`max.poll.records`가 너무 크고 처리 시간이 길면 `max.poll.interval.ms`를 초과해 Rebalance가 발생한다.

대응:
- `max.poll.records`를 줄인다.
- 처리 로직을 빠르게 만든다.
- 오래 걸리는 작업은 Worker Pool로 넘기되 offset commit 순서를 엄격히 관리한다.
- 처리 시간이 예측 불가능하면 별도 작업 큐로 분리한다.

---

## 5. Duplicate Processing

Kafka Consumer는 일반적으로 At-Least-Once 처리를 기본으로 본다.
즉, 장애 상황에서 중복 처리가 가능하다.

중복 발생 예:

```
1. Consumer가 메시지 A 처리 완료
2. DB 저장 성공
3. offset commit 전 장애
4. 재시작 후 메시지 A 다시 poll
5. DB 저장 재시도
```

대응 패턴:

```java
@Transactional
public void handle(OrderPaidEvent event) {
    try {
        processedEventRepository.saveAndFlush(new ProcessedEvent(event.eventId()));
    } catch (DataIntegrityViolationException e) {
        return;
    }

    paymentService.markPaid(event.orderId());
}
```

`existsById()`로 먼저 확인한 뒤 처리하는 방식은 TOCTOU 경쟁 조건이 있다.
`event_id` 유니크 제약에 먼저 INSERT하고, 중복 키 예외가 나면 이미 처리 중이거나 처리된 이벤트로 판단한다.

---

## 6. Poison Pill과 DLQ

역직렬화 실패, 필수 필드 누락, 비즈니스 규칙 위반 메시지는 재시도해도 성공하지 않는다.
이런 메시지를 계속 재시도하면 Consumer Lag가 증가하고 정상 메시지도 막힌다.

처리 전략:

```
1. Transient 실패: retry with backoff
2. N회 초과: DLQ 이동
3. Permanent 실패: 즉시 DLQ 이동
4. DLQ 메시지에는 원본 payload, topic, partition, offset, error_type 저장
```

DLQ 토픽 예:

```
orders.payment.dlq
```

DLQ 저장 필드:

| 필드 | 설명 |
|---|---|
| `source_topic` | 원본 topic |
| `source_partition` | 원본 partition |
| `source_offset` | 원본 offset |
| `payload` | 원본 메시지 |
| `error_type` | 실패 유형 |
| `retry_count` | 재시도 횟수 |
| `trace_id` | 추적 ID |

---

## 7. 실무 체크리스트

- Consumer 로직은 중복 처리에 안전해야 한다.
- offset commit은 side-effect 성공 이후에 수행한다.
- `max.poll.records`와 처리 시간을 함께 튜닝한다.
- Consumer Lag는 Group/Topic/Partition 단위로 모니터링한다.
- Rebalance 빈도가 높으면 Consumer 로그와 `max.poll.interval.ms` 초과 여부를 확인한다.
- Poison Pill은 DLQ로 격리하고 정상 메시지 처리를 막지 않는다.
- Partition 수는 처리 병렬성과 순서 보장 단위를 함께 고려해 정한다.

### 면접 포인트

- Q: Kafka Consumer에서 offset commit을 처리 전에 하면 어떤 문제가 생기는가?
- Q: Rebalance가 자주 발생하는 원인은 무엇인가?
- Q: At-Least-Once Consumer에서 중복 처리를 어떻게 방지하는가?
- Q: Consumer 수를 Partition 수보다 늘리면 처리량이 계속 증가하는가?
