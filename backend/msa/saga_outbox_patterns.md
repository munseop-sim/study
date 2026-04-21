# SAGA 패턴과 Outbox 패턴 — 분산 트랜잭션 설계

> **관련 문서**
> - [MSA 분산 트랜잭션 개요](./transaction.md) — 2PC, 보상 트랜잭션, SAGA 개념 배경, Eventuate/Axon 프레임워크
> - [시스템 설계 패턴 카탈로그](../system-design/patterns.md) — Outbox 패턴 소개, Event Sourcing 개요

## 1. 분산 트랜잭션 문제

마이크로서비스 아키텍처에서는 하나의 비즈니스 트랜잭션이 여러 서비스에 걸쳐 있다.
예를 들어 "국제 송금 접수" 요청은 다음 서비스를 순차적으로 거칠 수 있다.

```
TradeService  →  WalletService  →  PayoutService  →  NotificationService
(거래 생성)      (잔액 차감)       (지급 요청)         (알림 발송)
```

이 4단계가 모두 성공해야 완전한 트랜잭션이다.
단일 DB라면 `@Transactional` 하나로 해결되지만, 서비스마다 DB가 다르면 전통적인 방법으로는 원자성을 보장할 수 없다.

---

## 2. 2PC(Two-Phase Commit)의 한계

2PC는 분산 트랜잭션의 고전적 해결책이다.

```
Phase 1 (Prepare):  Coordinator → 모든 참여자에게 "커밋할 수 있어?" 질의
Phase 2 (Commit):   모두 Yes → Coordinator → "커밋해" 명령
                    하나라도 No → Coordinator → "롤백해" 명령
```

### 문제점

| 문제 | 설명 |
|---|---|
| **Blocking Protocol** | Phase 1 완료 후 Phase 2 응답을 기다리는 동안 모든 참여자가 락을 보유. 처리량 저하 |
| **Coordinator SPOF** | Coordinator가 Phase 2 전에 죽으면 참여자들은 락을 보유한 채 무한 대기 |
| **NoSQL/Kafka 미지원** | Kafka, Redis, Elasticsearch 등은 XA 프로토콜을 지원하지 않음 |
| **긴 응답시간** | 네트워크 왕복 2회 + 락 보유 시간 = 전체 처리량 급감 |

결론: MSA 환경에서 2PC는 현실적으로 쓰기 어렵다.

---

## 3. SAGA 패턴

SAGA는 분산 트랜잭션을 **일련의 로컬 트랜잭션** 체인으로 분해하는 패턴이다.
각 로컬 트랜잭션은 자신의 DB에만 쓰고, 완료 시 다음 단계를 트리거하는 이벤트/메시지를 발행한다.
어느 단계가 실패하면 이미 완료된 단계를 취소하는 **보상 트랜잭션(Compensating Transaction)**이 역순으로 실행된다.

### 3.1 Choreography(안무) vs Orchestration(오케스트레이션)

#### Choreography — 중앙 조율자 없음

```
TradeService          WalletService          PayoutService
    |                      |                      |
TRADE_CREATED ──────────>  |                      |
    |               WALLET_DEBITED ─────────────> |
    |                      |               PAYOUT_REQUESTED
    |                      |                      |
(실패 시) <── WALLET_DEBIT_FAILED                  |
    |                      |                      |
```

각 서비스는 이벤트를 구독하고 자신의 작업을 처리한 뒤 결과 이벤트를 발행한다.

**장점**
- 단순한 구조, 서비스 간 결합도 낮음
- 별도의 오케스트레이터 서비스 불필요

**단점**
- 전체 흐름이 여러 서비스에 분산되어 추적이 어려움
- 이벤트 폭발(Event Explosion): 각 서비스가 성공/실패 이벤트를 모두 발행해야 함
- 순환 이벤트 의존성 발생 가능
- 비즈니스 로직이 서비스에 분산되어 이해하기 어려움

#### Orchestration — 중앙 오케스트레이터

```
                  SagaOrchestrator
                 /       |        \
    TradeService   WalletService   PayoutService
```

오케스트레이터가 각 서비스에 커맨드를 보내고, 서비스는 결과를 오케스트레이터에게 응답한다.

**장점**
- 전체 흐름이 한 곳에 집중 → 가시성, 디버깅 용이
- 복잡한 보상 로직 처리 수월
- 이벤트 의존 관계 명확

**단점**
- 오케스트레이터가 비즈니스 로직에 과도하게 관여할 위험 (God Object)
- 오케스트레이터 서비스 자체가 단일 장애점이 될 수 있음
- 서비스 간 결합도가 오케스트레이터를 통해 생김

### 3.2 보상 트랜잭션(Compensating Transaction) 설계

보상 트랜잭션은 **이미 커밋된 로컬 트랜잭션의 효과를 논리적으로 취소**한다.
물리적 롤백이 아니라, 상태를 역으로 되돌리는 새로운 트랜잭션이다.

```
정방향 트랜잭션:
TRADE_CREATED → WALLET_DEBITED → PAYOUT_REQUESTED (실패)

보상 트랜잭션 (역순):
PAYOUT_FAILED → WALLET_CREDIT_BACK → TRADE_CANCELLED
```

**설계 원칙**
1. 모든 로컬 트랜잭션은 반드시 대응하는 보상 트랜잭션이 있어야 함
2. 보상 트랜잭션 자체도 재시도 가능하도록 멱등성 보장
3. 보상 불가 작업(환불 불가 수수료 등)은 피벗 트랜잭션 앞에 배치

```
Pivot Transaction(피벗): 이 지점 이전은 취소 가능, 이후는 취소 불가
예: 외부 결제사로 송금 완료 → 그 이후는 보상 불가
```

### 3.3 이벤트 순서 보장 이슈

Kafka는 파티션 내에서만 순서를 보장한다.

```
WALLET_DEBITED (partition 0)
WALLET_CREDIT_BACK (partition 1)
```

이 두 이벤트가 다른 파티션에 들어가면 소비 순서가 바뀔 수 있다.

**해결책**
- 같은 트랜잭션/엔티티 관련 이벤트는 동일 파티션 키 사용 (예: `userId`, `tradeId`)
- 이벤트에 sequenceNumber 또는 version 포함하여 소비자가 순서 검증
- 이벤트 소비자에서 낙관적 잠금(Optimistic Lock) 적용

---

## 4. Outbox 패턴

### 4.1 이중 쓰기(Dual Write) 문제

SAGA에서 로컬 트랜잭션 완료 후 이벤트를 발행하는 코드를 단순하게 작성하면:

```java
// 나쁜 예 — 이중 쓰기 문제
@Transactional
public void processWalletDebit(DebitCommand cmd) {
    wallet.debit(cmd.getAmount());          // DB 쓰기
    walletRepository.save(wallet);
    kafkaTemplate.send("wallet.debited", event); // Kafka 발행
}
// DB 커밋 성공 → Kafka 발행 실패 → 이벤트 유실
// DB 롤백       → Kafka 발행 성공 → 유령 이벤트
```

DB 트랜잭션과 Kafka 발행은 서로 다른 트랜잭션 경계이므로 원자성을 보장할 수 없다.

### 4.2 Outbox 테이블 구조

Outbox 패턴은 **이벤트 발행을 DB 트랜잭션에 포함**시킴으로써 이 문제를 해결한다.

```sql
CREATE TABLE outbox_event (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(100) NOT NULL,  -- 'Wallet', 'Trade', 'Payout'
    aggregate_id    VARCHAR(100) NOT NULL,  -- 엔티티 ID
    event_type      VARCHAR(100) NOT NULL,  -- 'WalletDebited', 'TradeCreated'
    payload         JSONB        NOT NULL,  -- 이벤트 본문 (직렬화된 JSON)
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    processed_at    TIMESTAMPTZ,            -- 발행 완료 시각 (null = 미발행)
    partition_key   VARCHAR(100)            -- Kafka 파티션 키
);
```

```java
// 좋은 예 — Outbox 패턴
@Transactional
public void processWalletDebit(DebitCommand cmd) {
    wallet.debit(cmd.getAmount());
    walletRepository.save(wallet);

    // 같은 트랜잭션 안에 이벤트 저장
    OutboxEvent outbox = OutboxEvent.of("Wallet", wallet.getId(), "WalletDebited", payload);
    outboxRepository.save(outbox);
    // 트랜잭션 커밋 → wallet 변경 + outbox 레코드가 원자적으로 저장
}
```

이제 별도의 프로세스가 outbox 테이블을 읽어 Kafka에 발행하면 된다.

### 4.3 Polling Publisher vs CDC (Debezium)

#### Polling Publisher

```
[Scheduler Thread]
  ↓ 주기적으로 SELECT (processed_at IS NULL)
  ↓ Kafka 발행
  ↓ UPDATE processed_at = now()
```

```java
@Scheduled(fixedDelay = 1000)
public void publishPendingEvents() {
    List<OutboxEvent> events = outboxRepository.findByProcessedAtIsNull();
    for (OutboxEvent event : events) {
        kafkaTemplate.send(event.getTopic(), event.getPartitionKey(), event.getPayload());
        event.markProcessed();
        outboxRepository.save(event);
    }
}
```

**장점**: 구현 단순, 추가 인프라 없음
**단점**: 폴링 주기만큼 지연 발생, DB에 부하, at-least-once 보장을 위한 멱등성 처리 필요

#### CDC(Change Data Capture) — Debezium

Debezium은 DB의 **WAL(Write-Ahead Log) / binlog**를 실시간으로 읽어 변경 이벤트를 Kafka로 스트리밍한다.

```
PostgreSQL WAL → Debezium Connector → Kafka Connect → Kafka Topic
```

```yaml
# Debezium PostgreSQL Connector 설정 예시
{
  "name": "outbox-connector",
  "config": {
    "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
    "database.hostname": "postgres",
    "table.include.list": "public.outbox_event",
    "transforms": "outbox",
    "transforms.outbox.type": "io.debezium.transforms.outbox.EventRouter",
    "transforms.outbox.table.field.event.key": "aggregate_id",
    "transforms.outbox.route.by.field": "aggregate_type"
  }
}
```

**장점**: 거의 실시간 발행, DB 폴링 부하 없음, 정확히 한 번 발행에 가까움
**단점**: Kafka Connect 클러스터 필요, Debezium 운영 복잡도, WAL 슬롯 관리 필요

| 비교 항목 | Polling Publisher | CDC (Debezium) |
|---|---|---|
| 지연 | 폴링 주기 (수백ms~수초) | 거의 실시간 (ms 단위) |
| 추가 인프라 | 없음 | Kafka Connect, Connector |
| DB 부하 | 폴링 쿼리 부하 있음 | WAL 읽기 (부하 적음) |
| 구현 복잡도 | 낮음 | 높음 |
| 운영 복잡도 | 낮음 | 높음 |
| 적합한 규모 | 소~중규모 | 대규모, 고처리량 |

---

## 5. 멱등성 소비자(Idempotent Consumer)

### 5.1 메시지 중복 수신 문제

Kafka는 **at-least-once** 전달을 보장한다. 즉, 네트워크 오류나 소비자 재시작 시 동일한 메시지가 여러 번 도달할 수 있다.

```
WALLET_DEBITED (eventId: "evt-123") → 소비자가 처리 완료 → 오프셋 커밋 전 소비자 재시작
→ WALLET_DEBITED (eventId: "evt-123") 재도착 → 잔액 이중 차감!
```

### 5.2 processedEventId 테이블 패턴

```sql
CREATE TABLE processed_event (
    event_id    VARCHAR(100) PRIMARY KEY,  -- Kafka 메시지의 고유 ID
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

```java
@Transactional
public void handleWalletDebited(WalletDebitedEvent event) {
    // 1. 이미 처리한 이벤트인지 확인
    if (processedEventRepository.existsById(event.getEventId())) {
        log.info("이미 처리된 이벤트 스킵: {}", event.getEventId());
        return;
    }

    // 2. 비즈니스 로직 수행
    payout.process(event.getAmount(), event.getRecipient());
    payoutRepository.save(payout);

    // 3. 처리된 이벤트 기록 (같은 트랜잭션)
    processedEventRepository.save(new ProcessedEvent(event.getEventId()));
}
```

**핵심**: 비즈니스 로직 수행 + processedEvent 저장이 같은 트랜잭션 안에 있어야 한다.

### 5.3 자연적 멱등성 활용

경우에 따라 별도 테이블 없이 자연적 멱등성을 설계할 수 있다.

```java
// 상태 전이 기반 멱등성: 이미 DEBITED 상태면 다시 차감하지 않음
if (wallet.getStatus() != WalletStatus.ACTIVE) {
    return; // 이미 처리됨
}

// UPSERT 기반 멱등성
payoutRepository.upsertByExternalId(event.getExternalId(), payoutData);
```

---

## 6. 결제/송금 도메인 적용 예시

### 6.1 외부 결제사 연동 (SAGA + Outbox)

```
[결제 요청]
    ↓ TradeService: 거래 생성 (상태: PENDING)
        → Outbox: TRADE_CREATED 이벤트 저장 (같은 트랜잭션)
    ↓ WalletService: 잔액 차감 (상태: DEBITED)
        → Outbox: WALLET_DEBITED 이벤트 저장
    ↓ ExternalPaymentService: 외부 결제사 API 호출 [피벗]
        → 성공: Outbox: PAYMENT_COMPLETED
        → 실패: Outbox: PAYMENT_FAILED (보상 트랜잭션 트리거)

[보상 흐름]
    PAYMENT_FAILED
    → WalletService: 잔액 환원 + Outbox: WALLET_CREDIT_BACK
    → TradeService: 거래 취소 + Outbox: TRADE_CANCELLED
```

### 6.2 CCF(Cross-Currency Flow) 연동 예시

```kotlin
// Kotlin + Spring 예시
@Service
class TradeCommandService(
    private val tradeRepository: TradeRepository,
    private val outboxRepository: OutboxEventRepository
) {
    @Transactional
    fun createTrade(command: CreateTradeCommand): TradeId {
        val trade = Trade.create(command)
        tradeRepository.save(trade)

        val event = TradeCreatedEvent(
            tradeId = trade.id,
            sendAmount = command.sendAmount,
            receiveCurrency = command.receiveCurrency,
            userId = command.userId
        )
        outboxRepository.save(
            OutboxEvent(
                aggregateType = "Trade",
                aggregateId = trade.id.value,
                eventType = "TradeCreated",
                payload = objectMapper.writeValueAsString(event),
                partitionKey = command.userId.value  // 사용자 단위 파티션 순서 보장
            )
        )
        return trade.id
    }
}
```

---

## 7. SAGA 상태 관리

SAGA는 장시간 실행될 수 있으므로 현재 상태를 저장해야 한다.

```sql
CREATE TABLE saga_state (
    saga_id         UUID PRIMARY KEY,
    saga_type       VARCHAR(100) NOT NULL,
    current_step    VARCHAR(100) NOT NULL,
    status          VARCHAR(50)  NOT NULL,  -- RUNNING, COMPLETED, COMPENSATING, FAILED
    context         JSONB        NOT NULL,  -- 각 단계의 결과/입력값
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
```

상태 저장은 오케스트레이터 패턴에서 특히 중요하다. 오케스트레이터가 재시작되어도 어느 단계까지 완료되었는지 파악하고 재개할 수 있다.

---

## 8. 요약 비교

| 항목 | 2PC | SAGA (Choreography) | SAGA (Orchestration) |
|---|---|---|---|
| 원자성 | 강한 원자성 | 최종 일관성 | 최종 일관성 |
| 가용성 | 낮음 (블로킹) | 높음 | 높음 |
| 복잡도 | 중간 | 낮음→높음 (이벤트 폭발) | 중간 |
| 가시성 | 중간 | 낮음 | 높음 |
| 적합한 상황 | 단일 DB 클러스터 | 간단한 플로우 | 복잡한 플로우 |

---

## 면접 포인트

### Q1. "SAGA와 2PC의 차이는 무엇인가요?"
- 2PC는 강한 원자성을 제공하지만 블로킹 프로토콜이고 코디네이터 SPOF 문제가 있다.
- SAGA는 로컬 트랜잭션 체인으로 분해하여 최종 일관성을 제공한다. 실패 시 보상 트랜잭션으로 취소한다.
- MSA 환경에서는 2PC보다 SAGA + Outbox 조합이 현실적이다.

### Q2. "Outbox 패턴이 왜 필요한가요?"
- DB 커밋과 Kafka 발행은 서로 다른 트랜잭션 경계를 가진다.
- DB에만 쓰고 Kafka 발행 전에 프로세스가 죽으면 이벤트가 유실된다.
- Outbox 패턴은 이벤트 저장을 DB 트랜잭션에 포함시켜 원자성을 보장하고, 별도 프로세스(Polling/CDC)가 Kafka로 릴레이한다.

### Q3. "Polling Publisher와 CDC 중 어떤 걸 선택하나요?"
- 처리량이 낮고 지연에 덜 민감하면 Polling Publisher (단순, 운영 편함).
- 처리량이 높고 실시간성이 중요하면 CDC (Debezium). 단 Kafka Connect 운영 부담이 있다.
- 실무에서는 초기 단계 Polling으로 시작해 트래픽 증가 시 CDC로 전환하는 경우가 많다.

### Q4. "멱등성 소비자를 어떻게 구현하나요?"
- processedEventId 테이블에 처리한 이벤트 ID를 저장한다.
- 비즈니스 로직 수행 + processedEvent 저장이 반드시 같은 트랜잭션이어야 한다.
- 또는 상태 전이나 UPSERT로 자연적 멱등성을 설계할 수도 있다.

### Q5. "SAGA에서 보상 트랜잭션이 실패하면 어떻게 되나요?"
- 보상 트랜잭션도 멱등하게 설계하고 재시도 가능하도록 만들어야 한다.
- 재시도 한도를 초과하면 Dead Letter Queue(DLQ)로 이동시키고 수동 처리/알람을 발생시킨다.
- 피벗 트랜잭션 이후의 단계(외부 결제 완료 후)는 보상이 불가하므로, 환불 프로세스를 별도로 운영해야 한다.

### Q6. "Choreography와 Orchestration 중 어느 것을 선택하나요?"
- 단계가 3개 이하, 흐름이 단순하면 Choreography.
- 단계가 많고 보상 로직이 복잡하면 Orchestration.
- 실무에서는 Orchestration이 디버깅과 모니터링이 용이하여 선호되는 편이다.
