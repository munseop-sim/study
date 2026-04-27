# DLQ(Dead Letter Queue) 설계 패턴

> **관련 문서**
> - [시스템 설계 패턴 카탈로그](./patterns.md) — Circuit Breaker, Retry, Outbox 패턴 개요
> - [SAGA 패턴과 Outbox 패턴](../msa/saga_outbox_patterns.md) — 분산 트랜잭션에서 DLQ 연계 흐름
> - [Observability](./observability.md) — DLQ 모니터링 지표 연계

---

## 1. DLQ 개요

### 정의

DLQ(Dead Letter Queue)는 소비자(Consumer)가 정상적으로 처리하지 못한 메시지를 격리하는 특수 큐다.
메인 파이프라인에서 처리 불가능한 메시지를 분리 저장하여, 이후 별도로 분석하거나 재처리할 수 있도록 한다.

```
[Producer] → [Main Queue/Topic] → [Consumer]
                                       │
                                  처리 실패 (N회 초과)
                                       │
                                       ▼
                                  [Dead Letter Queue]
                                       │
                          ┌────────────┼────────────┐
                          ▼            ▼             ▼
                     자동 재처리    수동 처리      알림만
                      워커           운영자         (Alert)
```

### 목적

| 목적 | 설명 |
|---|---|
| **메인 파이프라인 블로킹 방지** | 처리 불가 메시지가 정상 메시지의 처리를 막지 않도록 격리 |
| **데이터 유실 방지** | 폐기하지 않고 DLQ에 보존하여 나중에 재처리 가능 |
| **분석·디버깅** | 실패 원인을 구조화된 형태로 저장하여 장애 분석에 활용 |
| **운영 가시성** | DLQ 메시지 수 급증 자체가 파이프라인 이상 신호가 됨 |

### DLQ가 없을 때의 문제

**Poison Pill(독약 메시지)** 문제가 대표적이다.
소비자가 처리하지 못하는 메시지가 큐 맨 앞에 고착되면, 소비자는 이 메시지를 무한히 재시도하면서 뒤따라오는 정상 메시지들을 처리하지 못한다.

```
[Consumer]
  ↓ receive 메시지 A (포맷 오류)
  ↓ 처리 실패 → 재시도
  ↓ 처리 실패 → 재시도
  ↓ 처리 실패 → 재시도 ... (무한 반복)
  → 메시지 B, C, D ... 처리 불가 → Consumer Lag 급증 → 서비스 마비
```

---

## 2. 실패 분류 기준: Transient vs Permanent

DLQ 설계에서 가장 중요한 판단은 **이 실패가 재시도로 해결 가능한가** 여부다.

### Transient(일시적) 실패 — 재시도 대상

외부 환경이 일시적으로 불안정한 경우로, 잠시 후 재시도하면 성공할 가능성이 있다.

| 원인 | 예시 |
|---|---|
| 네트워크 타임아웃 | 외부 API 호출 타임아웃 |
| DB 커넥션 풀 고갈 | HikariCP connection timeout |
| 외부 서비스 일시 장애 | 503 Service Unavailable |
| 일시적 부하 급증 | 429 Too Many Requests |

**재시도 전략**

```
Exponential Backoff + Jitter:
  1회 실패 → 1s 대기
  2회 실패 → 2s + random(0~1s) 대기
  3회 실패 → 4s + random(0~2s) 대기
  ...
  N회 초과 → DLQ 이동
```

Jitter(무작위 지연)를 추가하는 이유: 동시에 여러 소비자가 재시도할 때 동기화된 재시도 폭풍(Thundering Herd)을 방지하기 위함이다.

### Permanent(영구적) 실패 — DLQ 이동 대상

재시도를 아무리 해도 성공할 수 없는 구조적 실패다.

| 원인 | 예시 |
|---|---|
| 메시지 포맷 오류 | 필수 필드 누락, 잘못된 JSON |
| 스키마 불일치 | Avro/Protobuf 역직렬화 실패 |
| 비즈니스 규칙 위반 | 잔액 부족, 이미 처리된 요청 |
| 리소스 미존재 | 404 Not Found (수신 계좌 없음) |
| 데이터 정합성 오류 | 외래키 제약 위반 |

### 분류 기준 예시

```
HTTP 응답 코드 기반:
  4xx (400·404·422) → Permanent → 즉시 DLQ 이동
  5xx (500·503)     → Transient → Retry
  429               → Transient → Retry (Rate Limit 해소 대기)

예외 클래스 기반:
  ConnectTimeoutException         → Transient
  SocketTimeoutException          → Transient
  ValidationException             → Permanent
  DataIntegrityViolationException → 상황에 따라 판단
    └─ 중복 키 위반: 이미 처리됨 → 멱등 처리 후 스킵
    └─ 외래키 위반: 의존 엔티티 미존재 → Permanent
  InsufficientBalanceException    → Permanent
  SchemaDeserializationException  → Permanent
```

### Spring Kafka 구현 예시

```java
@Bean
public DefaultErrorHandler errorHandler(KafkaTemplate<String, String> kafkaTemplate) {
    // Permanent 실패 예외 목록 — 즉시 DLQ 이동
    List<Class<? extends Exception>> notRetryable = List.of(
        ValidationException.class,
        InsufficientBalanceException.class,
        SchemaDeserializationException.class
    );

    DeadLetterPublishingRecoverer recoverer =
        new DeadLetterPublishingRecoverer(kafkaTemplate,
            (record, ex) -> new TopicPartition(record.topic() + ".dlq", -1));

    DefaultErrorHandler handler = new DefaultErrorHandler(
        recoverer,
        new ExponentialBackOffWithMaxRetries(3)  // Transient: 3회 재시도
    );

    // Permanent 실패는 재시도 없이 즉시 DLQ
    notRetryable.forEach(handler::addNotRetryableExceptions);
    return handler;
}
```

`TopicPartition(..., -1)`은 Spring Kafka가 DLQ 파티션을 직접 지정하지 않고 Producer의 파티셔너에 맡기겠다는 의미다.
DLQ 토픽의 파티션 수가 원본 토픽보다 적은 환경에서는 원본 파티션 번호를 그대로 지정하면 `IllegalArgumentException`이 발생할 수 있으므로, 파티션 수를 맞추거나 `-1`로 라우팅한다.

---

## 3. DLQ 메시지 스키마

DLQ에 저장되는 메시지는 원본 payload뿐 아니라, 나중에 재처리하거나 원인을 분석할 수 있는 충분한 메타데이터를 포함해야 한다.

### 스키마 구성 요소

| 필드 | 설명 | 필수 |
|---|---|---|
| `original_payload` | 원본 메시지 전문 (변형 없이 보존) | 필수 |
| `error.type` | 예외 클래스명 또는 오류 분류 | 필수 |
| `error.message` | 오류 메시지 | 필수 |
| `error.stack_trace` | 스택 트레이스 (선택적, 보안 고려) | 선택 |
| `error.retry_count` | DLQ 이동 전 재시도 횟수 | 필수 |
| `tracing.correlation_id` | 분산 트레이싱 연결 ID | 권장 |
| `tracing.trace_id` | OpenTelemetry trace/span ID | 권장 |
| `metadata.source_topic` | 원본 Kafka 토픽명 | 필수 |
| `metadata.source_partition` | 원본 파티션 번호 | 필수 |
| `metadata.source_offset` | 원본 오프셋 | 필수 |
| `metadata.first_failure_at` | 최초 실패 타임스탬프 | 필수 |
| `metadata.last_failure_at` | 마지막 실패 타임스탬프 | 필수 |
| `metadata.dead_lettered_at` | DLQ 이동 타임스탬프 | 필수 |

### 스키마 예시 (JSON)

```json
{
  "original_payload": {
    "event_type": "WalletWithdrawRequested",
    "wallet_id": "wlt-123",
    "amount": "50000.00",
    "currency": "KRW",
    "idempotency_key": "req-abc-789"
  },
  "error": {
    "type": "InsufficientBalanceException",
    "message": "Insufficient balance: requested=50000, available=30000",
    "retry_count": 0
  },
  "tracing": {
    "correlation_id": "d4f8b2c1-9e3a-4f2b-8c1d-e5f7a3b9d2c4",
    "trace_id": "7b3f9c2e1a4d5e6f"
  },
  "metadata": {
    "source_topic": "wallet.withdraw",
    "source_partition": 2,
    "source_offset": 10042,
    "first_failure_at": "2024-01-01T12:00:00Z",
    "last_failure_at": "2024-01-01T12:00:00Z",
    "dead_lettered_at": "2024-01-01T12:00:01Z"
  }
}
```

### DB 테이블 스키마 (금융 도메인 권장)

```sql
CREATE TABLE dead_letter_message (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    source_topic        VARCHAR(200) NOT NULL,
    source_partition    INT,
    source_offset       BIGINT,
    original_payload    JSONB        NOT NULL,
    error_type          VARCHAR(200) NOT NULL,
    error_message       TEXT         NOT NULL,
    retry_count         INT          NOT NULL DEFAULT 0,
    correlation_id      VARCHAR(100),
    trace_id            VARCHAR(100),
    first_failure_at    TIMESTAMPTZ  NOT NULL,
    last_failure_at     TIMESTAMPTZ  NOT NULL,
    dead_lettered_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- 재처리 추적
    status              VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    -- PENDING / REPROCESSING / RESOLVED / DISCARDED
    reprocessed_at      TIMESTAMPTZ,
    reprocess_result    TEXT,
    resolved_by         VARCHAR(100)
);

CREATE INDEX idx_dlm_status_dead_lettered ON dead_letter_message(status, dead_lettered_at);
CREATE INDEX idx_dlm_source_topic ON dead_letter_message(source_topic);
CREATE INDEX idx_dlm_correlation_id ON dead_letter_message(correlation_id);
```

---

## 4. DLQ 소비 주체 3가지

DLQ에 저장된 메시지를 어떻게 처리할지는 실패 원인과 비즈니스 특성에 따라 달라진다.

### 4.1 자동 재처리 워커

Transient 실패가 영구화된 케이스(외부 서비스 장기 장애 후 복구 등)에 적합하다.
DLQ에 도착한 시점에는 처리 불가했으나, 일정 시간 후 외부 의존성이 복구되면 재처리가 가능한 경우다.

```
[DLQ 자동 재처리 워커]
  ↓ PENDING 상태의 DLQ 메시지 조회 (주기적 또는 이벤트 기반)
  ↓ error.type 확인 → Transient 계열인지 검증
  ↓ 원본 payload를 메인 파이프라인으로 재발행
  ↓ 성공: status = RESOLVED, reprocessed_at 기록
  ↓ 실패: retry_count 증가, 임계치 초과 시 운영자 알림
```

```java
@Scheduled(fixedDelay = 60_000)  // 1분마다
public void reprocessTransientFailures() {
    List<DeadLetterMessage> pending = dlqRepository
        .findByStatusAndErrorTypeIn(
            "PENDING",
            TRANSIENT_ERROR_TYPES,
            PageRequest.of(0, 100)
        );

    for (DeadLetterMessage dlm : pending) {
        try {
            dlm.markReprocessing();
            kafkaTemplate.send(dlm.getSourceTopic(), dlm.getOriginalPayload());
            dlm.markResolved();
        } catch (Exception e) {
            dlm.incrementRetryCount();
            if (dlm.getRetryCount() > MAX_AUTO_RETRY) {
                alertService.notifyOncall(dlm);
            }
        }
        dlqRepository.save(dlm);
    }
}
```

### 4.2 인간 운영자 직접 처리

비즈니스 규칙 위반이나 데이터 보정이 필요한 케이스는 자동 처리가 불가능하다.

- 잔액 부족: 고객 입금 확인 후 재처리
- 수신 계좌 미존재: 계좌 정보 확인 및 수정 후 재처리
- 스키마 불일치: 원본 데이터 보정 후 재발행
- 비즈니스 예외: 수동 데이터 보정 또는 폐기 결정

운영자는 관리자 UI 또는 CLI 도구를 통해 DLQ 메시지를 조회하고, 원인 분석 후 재처리 또는 폐기 여부를 결정한다.

### 4.3 알림만 (Alert-Only)

즉시 재처리가 불가하고 온콜 담당자가 runbook을 직접 수행해야 하는 케이스다.

```
DLQ 메시지 수신
  → 자동 분류: Alert-Only 유형 확인
  → Slack/PagerDuty 알림 발송 (원본 payload + 오류 요약 포함)
  → 온콜 담당자 runbook 수행
  → 처리 완료 시 DLQ 레코드 status 업데이트
```

### 혼합 전략 (실무 권장)

```
DLQ 메시지 도착
  │
  ├─ Transient 계열 → 자동 재처리 워커 (N회 시도)
  │       └─ N회 실패 시 → 운영자 알림 + 수동 검토
  │
  ├─ Permanent / 비즈니스 위반 → 운영자 알림 + 수동 처리
  │
  └─ 알 수 없는 오류 → Alert-Only → 온콜 알람
```

---

## 5. DLQ 저장소 선택: Kafka vs SQS vs DB 테이블

### 비교표

| 기준 | Kafka DLQ 토픽 | SQS DLQ | DB 테이블 |
|---|---|---|---|
| 처리량 | 매우 높음 | 높음 | 낮음~중간 |
| 순서 보장 | 파티션 내 보장 | 불보장 | `ORDER BY` 로 가능 |
| 쿼리·분석 | 어려움 (Consumer 필요) | 어려움 | SQL로 용이 |
| TTL 설정 | `retention.ms` | `MessageRetentionPeriod` | 배치 삭제 |
| 운영 복잡도 | 중간 | 낮음 | 낮음 |
| 재처리 편의성 | 전용 Consumer 필요 | 가시성 타임아웃 활용 | SQL UPDATE로 간단 |
| 감사 로그 | 별도 구성 필요 | 별도 구성 필요 | 컬럼으로 자연스럽게 |
| 선택 기준 | Kafka 기반 고처리량 파이프라인 | AWS 환경, 서버리스 | 소량·금융 정합성 |

### Kafka DLQ 토픽 설정 예시

```yaml
# Kafka 토픽 설정 — wallet.withdraw.dlq
retention.ms=604800000       # 7일 보관
retention.bytes=-1           # 용량 제한 없음
cleanup.policy=delete
min.insync.replicas=2        # 데이터 내구성 강화
```

```java
// Spring Kafka DeadLetterPublishingRecoverer 설정
@Bean
public DeadLetterPublishingRecoverer deadLetterRecoverer(
        KafkaTemplate<String, String> kafkaTemplate) {
    return new DeadLetterPublishingRecoverer(kafkaTemplate,
        (record, ex) -> {
            // 원본 토픽명 + ".dlq" 로 라우팅
            return new TopicPartition(record.topic() + ".dlq", -1);
        });
}
```

주의: 원본 파티션 번호를 DLQ에도 유지하려면 DLQ 토픽 파티션 수가 원본 토픽 이상이어야 한다.
파티션 수가 다르면 `-1`을 사용해 Producer 파티셔너에 위임하거나, 별도의 파티션 매핑 로직을 둔다.

### SQS DLQ 설정 예시 (AWS)

```json
{
  "maxReceiveCount": 3,
  "deadLetterTargetArn": "arn:aws:sqs:ap-northeast-2:123456789:wallet-withdraw-dlq"
}
```

```yaml
# SQS DLQ 속성
MessageRetentionPeriod: 1209600  # 14일 (최대)
VisibilityTimeout: 300           # 재처리 시도 시 5분 점유
```

### DB 테이블 DLQ 권장 상황 (금융 도메인)

금융 도메인에서는 DB 테이블 DLQ를 권장한다.

- SQL 조회로 특정 기간·오류 유형·상태별 분석 용이
- 감사 로그(`resolved_by`, `reprocessed_at`) 자연스럽게 기록
- 수동 데이터 보정 후 재처리 여부를 트랜잭션 내에서 처리 가능
- 처리 건수가 Kafka/SQS 대비 적어 DB 부하 수용 가능

```sql
-- 금융 DLQ 운영 조회 예시
-- 오늘 발생한 잔액 부족 오류 목록
SELECT id, source_topic, original_payload->>'wallet_id' AS wallet_id,
       error_message, dead_lettered_at
FROM dead_letter_message
WHERE error_type = 'InsufficientBalanceException'
  AND dead_lettered_at >= CURRENT_DATE
  AND status = 'PENDING'
ORDER BY dead_lettered_at DESC;
```

---

## 6. Poison Pill 감지 및 처리

### 정의

어떤 시도를 해도 소비자가 처리할 수 없는 메시지를 **Poison Pill**이라 한다.
메시지 자체에 근본적인 결함이 있어 재시도 횟수와 관계없이 항상 실패한다.

### 증상

- 동일 메시지로 Consumer가 반복 재시작 (Crash Loop)
- Consumer Lag이 특정 오프셋에서 전혀 감소하지 않음
- 동일 `correlation_id`의 오류 로그가 지속 반복

### 감지

```
모니터링 지표:
  retry_count > POISON_PILL_THRESHOLD (예: 5회)
  → DLQ 이동 + 즉시 Alert 발송

Kafka 기반 감지:
  동일 partition + offset의 처리 실패가 N회 반복
  → partition.assignment.strategy 재확인
  → 해당 오프셋의 메시지 dump 후 분석
```

```java
@KafkaListener(topics = "wallet.withdraw")
public void consume(ConsumerRecord<String, String> record) {
    try {
        messageProcessor.process(record.value());
    } catch (Exception e) {
        int retryCount = retryCountTracker.increment(record.topic(),
            record.partition(), record.offset());

        if (retryCount > POISON_PILL_THRESHOLD) {
            // 즉시 DLQ 이동 + 알림
            dlqPublisher.send(record, e, retryCount);
            alertService.notifyPoisonPill(record, e);
            // 오프셋 커밋하여 파이프라인 재개
            acknowledgment.acknowledge();
        } else {
            throw e;  // 재시도 유도
        }
    }
}
```

### 처리 전략

| 전략 | 설명 | 적합한 상황 |
|---|---|---|
| **Skip + DLQ 이동** | 오프셋 커밋 후 다음 메시지로 진행, 격리 보관 | 파이프라인 연속성이 중요한 경우 |
| **Quarantine Topic** | 분석용 별도 토픽으로 라우팅 (`wallet.withdraw.quarantine`) | 상세 분석이 필요한 경우 |
| **Circuit Breaker 연계** | Poison Pill 감지 시 해당 파티션 처리 일시 중단 | 연쇄 장애 방지 |

### 방지 전략

```
1. 스키마 레지스트리 (Avro / Protobuf)
   Producer 단에서 스키마 검증 → 포맷 오류 메시지 발행 차단

2. 입력 유효성 검사
   Producer 또는 API Gateway에서 필수 필드, 비즈니스 규칙 선행 검증

3. 스키마 버전 호환성 관리
   Backward / Forward Compatibility 정책으로 스키마 불일치 방지

4. 소비자 방어 코드
   역직렬화 실패를 최상위에서 캐치하여 즉시 DLQ 이동
```

```java
@KafkaListener(topics = "wallet.withdraw")
public void consume(String message) {
    WalletWithdrawEvent event;
    try {
        event = objectMapper.readValue(message, WalletWithdrawEvent.class);
    } catch (JsonProcessingException e) {
        // 역직렬화 실패 = Permanent 실패 → 재시도 없이 즉시 DLQ
        dlqPublisher.sendImmediate(message, e);
        return;
    }
    // 정상 처리 로직...
}
```

---

## 7. DLQ 재처리 전략 및 멱등성

### 재처리 원칙

DLQ 재처리에서 가장 중요한 원칙은 **중복 처리 방지**다.
DLQ에서 꺼낸 메시지를 다시 메인 파이프라인으로 보내면, 이미 일부가 처리되었을 가능성이 있다.

```
원칙:
  1. 재처리 전 원인 분석 필수 (같은 실패 반복 방지)
  2. 원본 idempotency_key 재사용 → 소비자 멱등 처리
  3. 재처리 결과 추적 (reprocessed_at, reprocess_result 기록)
  4. 재처리 자체도 트랜잭션으로 관리 (재처리 시작~완료 원자성)
```

### 멱등성 보장 방법

```java
// 소비자 측 멱등 처리 — idempotency_key 기반
@Transactional
public void processWithdraw(WalletWithdrawEvent event) {
    // 이미 처리된 요청인지 확인 (idempotency_key = 원본 그대로 사용)
    if (processedRequestRepository.existsByIdempotencyKey(event.getIdempotencyKey())) {
        log.info("이미 처리된 요청 스킵: {}", event.getIdempotencyKey());
        return;
    }

    // 비즈니스 로직 수행
    wallet.withdraw(event.getAmount());
    walletRepository.save(wallet);

    // 처리 완료 기록 (같은 트랜잭션)
    processedRequestRepository.save(
        ProcessedRequest.of(event.getIdempotencyKey())
    );
}
```

### TTL 기반 자동 재처리

외부 의존성 복구 후 자동으로 재처리가 시도되도록 TTL을 설정한다.

```java
// DLQ 메시지 자동 재처리 정책
public class DlqReprocessPolicy {
    // Transient 실패: 10분 후 자동 재시도
    public static final Duration TRANSIENT_REPROCESS_DELAY = Duration.ofMinutes(10);
    // 외부 API 오류: 1시간 후 자동 재시도
    public static final Duration EXTERNAL_API_REPROCESS_DELAY = Duration.ofHours(1);
    // Permanent 실패: 자동 재처리 없음 → 수동 처리
    public static final Duration PERMANENT_NO_AUTO_REPROCESS = null;
}

@Scheduled(fixedDelay = 60_000)
public void autoReprocess() {
    OffsetDateTime threshold = OffsetDateTime.now()
        .minus(DlqReprocessPolicy.TRANSIENT_REPROCESS_DELAY);

    List<DeadLetterMessage> candidates = dlqRepository
        .findByStatusAndErrorCategoryAndDeadLetteredAtBefore(
            "PENDING", "TRANSIENT", threshold
        );
    // ... 재처리 로직
}
```

### 수동 재처리 흐름

```
[운영자]
  ↓ 관리자 UI에서 DLQ 메시지 조회 (source_topic, error_type 필터)
  ↓ 원본 payload 확인 및 원인 분석
  ↓ 필요 시 payload 수정 (데이터 보정)
  ↓ "재처리" 버튼 클릭 또는 CLI 명령
  ↓ 시스템: original_payload → 메인 토픽 재발행
  ↓ status = REPROCESSING → 완료 시 RESOLVED
  ↓ resolved_by, reprocessed_at, reprocess_result 기록
```

---

## 8. 운영 runbook 연결 포인트

### DLQ 모니터링 지표

| 지표 | 설명 | 알람 기준 예시 |
|---|---|---|
| `dlq_message_count` | DLQ에 누적된 미처리 메시지 수 | PENDING > 100건 |
| `dlq_incoming_rate` | 분당 DLQ 유입 건수 | 5분 내 50건 초과 |
| `dlq_oldest_message_age` | 가장 오래된 미처리 메시지 경과 시간 | 24시간 초과 |
| `dlq_reprocess_failure_rate` | 재처리 실패율 | 50% 초과 |
| `consumer_lag` | 메인 토픽 Consumer Lag | 파티션별 임계치 |

```yaml
# Prometheus + Alertmanager 알람 예시
groups:
  - name: dlq_alerts
    rules:
      - alert: DLQMessageSpike
        expr: increase(dlq_incoming_total[5m]) > 50
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "DLQ 메시지 급증 — 5분 내 {{ $value }}건 유입"

      - alert: DLQOldestMessageStale
        expr: dlq_oldest_message_age_hours > 24
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "DLQ 미처리 메시지 24시간 초과"
```

### 온콜 대응 체크리스트 (Runbook)

```
[DLQ 메시지 급증 알람 수신 시]

1. 원본 파이프라인 상태 확인
   □ 메인 Consumer가 정상 동작 중인지 확인
   □ Consumer Lag 추세 확인 (증가 중 / 감소 중)
   □ 소비자 로그에서 반복 오류 패턴 식별

2. 오류 분류
   □ DLQ 메시지의 error_type 분포 확인
   □ Transient vs Permanent 비율 파악
   □ 특정 source_topic에 집중되어 있는지 확인

3. 원본 payload 확인
   □ 샘플 메시지 원본 payload 내용 검토
   □ Poison Pill 여부 판단 (동일 offset 반복 여부)
   □ 스키마 변경 배포 여부 확인

4. 대응 결정
   □ Transient 계열 + 외부 의존성 복구됨 → 자동 재처리 워커 트리거
   □ Permanent 계열 → 데이터 보정 후 수동 재처리 or 폐기 결정
   □ Poison Pill 확인 → 격리 후 스키마/배포 팀에 에스컬레이션

5. 처리 완료 확인
   □ DLQ PENDING 건수 감소 확인
   □ 메인 파이프라인 정상화 확인
   □ 인시던트 리포트 작성
```

### SLA 설정 예시

```
DLQ 메시지 해소 SLA:
  - Critical (결제/출금 관련): 4시간 내 해소
  - High (알림 관련): 24시간 내 해소
  - Medium (배치 관련): 72시간 내 해소

보존 정책:
  - RESOLVED: 90일 보관 후 아카이빙
  - DISCARDED: 30일 보관 후 삭제
  - PENDING: 무기한 보관 (해소 전까지)
```

---

## 면접 포인트

### Q1. "Transient 실패와 Permanent 실패를 어떻게 코드로 구분하는가?"

HTTP 응답 코드와 예외 클래스를 기준으로 화이트리스트/블랙리스트를 관리한다.
Spring Kafka의 `DefaultErrorHandler`에서 `addNotRetryableExceptions()`으로 Permanent 예외 목록을 등록하면, 해당 예외 발생 시 재시도 없이 즉시 DLQ 복구자(Recoverer)를 호출한다.
`ValidationException`, `InsufficientBalanceException`, `SchemaDeserializationException` 등은 Not-Retryable로 등록하고, 나머지 `RuntimeException` 계열은 최대 N회 exponential backoff로 재시도 후 DLQ 이동한다.

### Q2. "DLQ 저장소로 Kafka 토픽 vs DB 테이블 중 어떤 것을 선택하겠는가?"

처리량과 도메인 특성에 따라 다르게 선택한다.
고처리량 이벤트 스트리밍 파이프라인(로그, 통계 등)은 Kafka DLQ 토픽이 적합하다. 처리량이 높고 이미 Kafka 인프라가 있기 때문이다.
반면 금융 도메인(출금, 송금 등)은 DB 테이블 DLQ를 권장한다. 이유는 SQL로 오류 유형·기간별 분석이 쉽고, `resolved_by`·`reprocessed_at` 같은 감사 로그를 자연스럽게 기록할 수 있으며, 데이터 보정 후 재처리 여부를 동일 트랜잭션 내에서 처리할 수 있기 때문이다. 금융 처리량은 Kafka 대비 낮아 DB 부하가 문제되지 않는 경우가 대부분이다.

### Q3. "Poison Pill이 발생했을 때 Consumer 파이프라인을 어떻게 보호하는가?"

재시도 횟수 임계치(`retry_count > threshold`)를 감지하여 해당 메시지를 즉시 DLQ로 격리하고 오프셋을 커밋(acknowledge)한다.
오프셋을 커밋해야 Consumer가 다음 메시지로 이동할 수 있다. 격리 후에는 즉시 온콜 알림을 발송하고, 원본 payload와 오프셋 정보를 DLQ에 보존한다.
방지 관점에서는 Producer 측에 스키마 레지스트리(Avro/Protobuf)를 도입하여 포맷 오류 메시지가 애초에 발행되지 않도록 하고, Consumer 상단에서 역직렬화 실패를 별도 처리하여 파이프라인 중단 없이 DLQ로 라우팅한다.

### Q4. "DLQ 재처리 시 중복 처리를 어떻게 방지하는가?"

소비자에 멱등성을 설계하는 것이 핵심이다.
원본 메시지의 `idempotency_key`를 그대로 재사용하여 재처리한다. 소비자는 비즈니스 로직 수행 전에 `processed_request` 테이블에서 해당 키의 존재 여부를 확인하고, 이미 처리된 경우 스킵한다. 이 확인과 처리 기록이 같은 트랜잭션 안에 있어야 한다.
또한 비즈니스 로직 자체를 멱등하게 설계할 수 있다. 예를 들어 지갑 상태가 이미 `WITHDRAWN`이면 중복 차감을 시도하지 않는 상태 전이 기반 멱등성, 또는 UPSERT 기반 처리도 활용할 수 있다.

### Q5. "DLQ 모니터링 지표와 알람 기준은 어떻게 설계하겠는가?"

4가지 핵심 지표를 모니터링한다.

첫째, `dlq_incoming_rate`(유입 속도): 5분 내 N건 초과 시 알람. 파이프라인 이상 조기 탐지용이다.

둘째, `dlq_message_count`(누적 미처리 건수): 임계치 초과 시 알람. 백로그 누적 방지용이다.

셋째, `dlq_oldest_message_age`(가장 오래된 미처리 메시지 경과 시간): 24시간 초과 시 알람. SLA 위반 방지용이다.

넷째, `consumer_lag`(메인 토픽 Lag): Poison Pill 또는 Consumer 문제를 간접 탐지한다.

알람은 Prometheus + Alertmanager 또는 CloudWatch 기반으로 구성하고, 알람 메시지에 원본 토픽, 오류 유형, 대표 메시지 샘플을 포함시켜 온콜 담당자가 즉시 원인을 파악할 수 있도록 한다.
