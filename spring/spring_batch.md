# Spring Batch & Transaction Propagation

## 1. Spring Batch 기본 구조

### Job → Step → Tasklet/Chunk 계층 구조

Spring Batch의 실행 단위는 **Job > Step > Tasklet/Chunk** 3단계 계층으로 구성된다.

```
Job (하나의 배치 작업)
 ├─ Step 1 (Tasklet 기반)
 │    └─ Tasklet: 단일 작업 수행 (테이블 초기화, 파일 삭제 등)
 ├─ Step 2 (Chunk 기반)
 │    └─ ItemReader → ItemProcessor → ItemWriter
 └─ Step 3 (Chunk 기반)
      └─ ItemReader → ItemProcessor → ItemWriter
```

| 구성요소 | 역할 | 특징 |
|---|---|---|
| **Job** | 배치 작업의 최상위 단위 | 하나 이상의 Step으로 구성 |
| **Step** | Job 내의 독립적 처리 단계 | Tasklet 또는 Chunk 방식 선택 |
| **Tasklet** | 단일 작업 수행 | 반복 호출 가능, `RepeatStatus.FINISHED` 반환 시 종료 |
| **Chunk** | 데이터를 묶음 단위로 처리 | Reader → Processor → Writer 파이프라인 |

### Chunk 기반 처리: ItemReader → ItemProcessor → ItemWriter

Chunk 기반 처리는 대량 데이터를 **일정 크기(Chunk Size)로 묶어 트랜잭션 단위로 처리**하는 방식이다.

```java
@Bean
public Step orderProcessStep(JobRepository jobRepository,
                              PlatformTransactionManager transactionManager) {
    return new StepBuilder("orderProcessStep", jobRepository)
            .<Order, OrderResult>chunk(100, transactionManager)  // Chunk Size = 100
            .reader(orderReader())       // 1건씩 읽어서
            .processor(orderProcessor()) // 1건씩 가공하고
            .writer(orderWriter())       // 100건 모아서 한 번에 쓰기
            .build();
}
```

**처리 흐름:**
1. `ItemReader`가 데이터를 **1건씩** 읽음
2. `ItemProcessor`가 읽은 데이터를 **1건씩** 가공/필터링
3. Chunk Size만큼 모이면 `ItemWriter`가 **묶음으로 한 번에** 저장
4. 트랜잭션 커밋 후 다음 Chunk 시작

```
[Chunk 1 - TX 시작]
  Reader: item1 → Processor: result1
  Reader: item2 → Processor: result2
  ...
  Reader: item100 → Processor: result100
  Writer: [result1, result2, ..., result100] 한 번에 저장
[Chunk 1 - TX 커밋]

[Chunk 2 - TX 시작]
  Reader: item101 → Processor: ...
  ...
```

### Chunk Size가 성능에 미치는 영향

Chunk Size는 **커밋 간격, 메모리 사용량, 실패 시 재처리 범위**에 직접적인 영향을 미친다.

| 항목 | Chunk Size 작을 때 (10) | Chunk Size 클 때 (1000) |
|---|---|---|
| **커밋 빈도** | 자주 커밋 → DB I/O 증가 | 적게 커밋 → DB I/O 감소 |
| **메모리 사용량** | 적음 (10건만 메모리에 유지) | 많음 (1000건 메모리에 유지) |
| **실패 시 재처리 범위** | 최대 10건 재처리 | 최대 1000건 재처리 |
| **네트워크 왕복** | Writer 호출 빈번 | Writer 호출 적음 (벌크 효율적) |
| **트랜잭션 유지 시간** | 짧음 → 락 경합 적음 | 길음 → 락 경합 증가 가능 |

**실무 가이드라인:**
- 일반적으로 **100~500** 사이를 권장
- 건당 처리 시간이 긴 경우 → Chunk Size를 줄여서 트랜잭션 유지 시간 단축
- 단순 INSERT 위주 → Chunk Size를 늘려서 벌크 효율 극대화
- OOM 위험이 있는 대용량 데이터 → Chunk Size를 줄이고 페이징 Reader 사용

---

## 2. Spring Batch 트랜잭션 관리

### Chunk 단위 트랜잭션

Spring Batch에서 **각 Chunk는 하나의 트랜잭션**으로 실행된다. Chunk 내 모든 처리(Reader → Processor → Writer)가 성공하면 커밋, 실패하면 해당 Chunk만 롤백된다.

```
Job 실행
├─ Step 실행
│   ├─ [TX-1] Chunk 1: Read 100건 → Process → Write → COMMIT ✅
│   ├─ [TX-2] Chunk 2: Read 100건 → Process → Write → COMMIT ✅
│   ├─ [TX-3] Chunk 3: Read 100건 → Process → Write → ROLLBACK ❌ (에러 발생)
│   └─ Step 실패
└─ Job 실패
```

### Step 실패 시 마지막 성공 Chunk까지 커밋 유지

위 예시에서 Chunk 3에서 실패하더라도 **Chunk 1, 2의 커밋은 유지**된다. Spring Batch는 `JobRepository`에 실행 메타데이터를 저장하여 **재시작 시 마지막 성공 지점부터 재개**할 수 있다.

```java
// 재시작 시 Chunk 3부터 처리 재개
// JobRepository의 BATCH_STEP_EXECUTION 테이블에 기록된 정보:
// - READ_COUNT: 200 (Chunk 1 + Chunk 2에서 읽은 건수)
// - WRITE_COUNT: 200
// - COMMIT_COUNT: 2
// - ROLLBACK_COUNT: 1
```

**재시작 조건:**
- `Job`이 `restartable(true)`로 설정되어야 함 (기본값: true)
- `ItemReader`가 상태를 저장하는 구현체여야 함 (예: `JdbcPagingItemReader`)
- 동일한 `JobParameters`로 재실행

### Skip/Retry 정책과 트랜잭션 롤백 관계

#### Skip 정책

특정 예외 발생 시 해당 아이템을 **건너뛰고 계속 진행**한다. Skip 발생 시 **해당 Chunk 트랜잭션은 롤백 후, 실패한 아이템을 제외하고 1건씩 재처리**한다.

```java
@Bean
public Step step(JobRepository jobRepository,
                 PlatformTransactionManager transactionManager) {
    return new StepBuilder("step", jobRepository)
            .<Order, OrderResult>chunk(100, transactionManager)
            .reader(reader())
            .processor(processor())
            .writer(writer())
            .faultTolerant()
            .skip(ValidationException.class)   // 이 예외 발생 시 건너뛰기
            .skipLimit(10)                      // 최대 10건까지 건너뛰기 허용
            .noSkip(FatalException.class)       // 이 예외는 건너뛰지 않음
            .build();
}
```

**Skip 시 내부 동작:**
```
[Chunk - TX 시작]
  item1 ~ item100 처리 중 item50에서 ValidationException 발생
[Chunk - TX 롤백]

[재처리 - 1건씩 개별 TX]
  [TX] item1 → 성공 → COMMIT
  [TX] item2 → 성공 → COMMIT
  ...
  [TX] item50 → Skip (건너뜀)
  ...
  [TX] item100 → 성공 → COMMIT
```

#### Retry 정책

특정 예외 발생 시 해당 아이템을 **재시도**한다. Retry는 **트랜잭션 롤백 없이 Processor 단계에서 재시도**한다.

```java
@Bean
public Step step(JobRepository jobRepository,
                 PlatformTransactionManager transactionManager) {
    return new StepBuilder("step", jobRepository)
            .<Order, OrderResult>chunk(100, transactionManager)
            .reader(reader())
            .processor(processor())
            .writer(writer())
            .faultTolerant()
            .retry(TransientException.class)  // 일시적 오류 재시도
            .retryLimit(3)                    // 최대 3회 재시도
            .build();
}
```

| 정책 | 트랜잭션 영향 | 적합한 예외 유형 |
|---|---|---|
| **Skip** | Chunk 롤백 후 1건씩 재처리 | 데이터 품질 문제 (파싱 오류, 유효성 실패) |
| **Retry** | 롤백 없이 재시도 (Processor에서) | 일시적 오류 (네트워크 타임아웃, 락 경합) |
| **Skip + Retry** | Retry 실패 후 Skip으로 전환 가능 | 재시도로 복구될 수 있지만 보장은 못하는 경우 |

---

## 3. 트랜잭션 전파 속성 (Propagation) 상세

`@Transactional(propagation = ...)` 으로 설정하며, 트랜잭션 경계에서 **기존 트랜잭션이 있을 때/없을 때**의 동작을 결정한다.

### 7가지 전파 속성 비교표

| 속성 | 기존 TX 있을 때 | 기존 TX 없을 때 | 주요 사용 사례 |
|---|---|---|---|
| **REQUIRED** | 참여 | 생성 | 기본값, 대부분의 서비스 메서드 |
| **REQUIRES_NEW** | 기존 TX 일시중단 + 새 TX 생성 | 생성 | 독립적 로그/감사, 배치 개별 건 커밋 |
| **MANDATORY** | 참여 | 예외 발생 (`IllegalTransactionStateException`) | TX가 반드시 있어야 하는 로직 보장 |
| **SUPPORTS** | 참여 | TX 없이 실행 | 읽기 전용 조회 로직 |
| **NOT_SUPPORTED** | 기존 TX 일시중단 + TX 없이 실행 | TX 없이 실행 | 비트랜잭션 작업 (외부 API 호출 등) |
| **NESTED** | SAVEPOINT 생성 후 중첩 TX | 생성 | 부분 롤백이 필요한 로직 |
| **NEVER** | 예외 발생 (`IllegalTransactionStateException`) | TX 없이 실행 | TX 사용 금지를 명시적으로 보장 |

### 각 전파 속성 동작 예시

#### REQUIRED (기본값)

```java
@Transactional  // propagation = REQUIRED (기본값)
public void outerMethod() {
    // TX-1 시작
    innerService.innerMethod();  // TX-1에 참여
    // TX-1 커밋 (또는 롤백)
}

@Transactional
public void innerMethod() {
    // outerMethod에서 호출 → TX-1에 참여 (새 TX 생성하지 않음)
    // 단독 호출 → 새 TX 생성
}
```

#### REQUIRES_NEW

```java
@Transactional
public void outerMethod() {
    // TX-1 시작
    repository.save(entity1);
    
    innerService.audit();  // TX-1 일시중단, TX-2 시작
    // TX-2 커밋/롤백 후 TX-1 재개
    
    repository.save(entity2);
    // TX-1 커밋
}

@Transactional(propagation = Propagation.REQUIRES_NEW)
public void audit() {
    // TX-2 (독립 트랜잭션)
    auditRepository.save(auditLog);
    // TX-2 커밋 → TX-1이 롤백되어도 이 커밋은 유지됨
}
```

#### MANDATORY

```java
@Transactional(propagation = Propagation.MANDATORY)
public void deductBalance(BigDecimal amount) {
    // 반드시 상위 TX가 있어야 함
    // TX 없이 호출하면 IllegalTransactionStateException 발생
    wallet.deduct(amount);
}
```

#### NESTED

```java
@Transactional
public void processOrder(Order order) {
    // TX-1 시작
    orderRepository.save(order);

    try {
        notificationService.sendNotification(order);  // SAVEPOINT 생성
    } catch (Exception e) {
        // SAVEPOINT까지만 롤백, order 저장은 유지
        log.warn("알림 전송 실패, 주문은 정상 처리");
    }
    // TX-1 커밋 (order 저장 포함)
}

@Transactional(propagation = Propagation.NESTED)
public void sendNotification(Order order) {
    // SAVEPOINT 이후 실행
    // 실패 시 SAVEPOINT까지만 롤백
}
```

---

## 4. REQUIRES_NEW의 실무 활용과 주의사항

### 독립 트랜잭션이 필요한 사례

#### 사례 1: 로그/감사 이력 기록

비즈니스 로직이 실패하더라도 **감사 이력은 반드시 남아야** 하는 경우.

```java
@Service
public class TransferService {

    @Transactional
    public void transfer(TransferRequest request) {
        try {
            walletService.deduct(request.getFrom(), request.getAmount());
            walletService.credit(request.getTo(), request.getAmount());
        } catch (Exception e) {
            // 송금 실패 → TX 롤백 예정
            // 그러나 감사 로그는 독립 TX로 반드시 저장
            auditService.logFailure(request, e);  // REQUIRES_NEW
            throw e;
        }
        auditService.logSuccess(request);  // REQUIRES_NEW
    }
}

@Service
public class AuditService {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logFailure(TransferRequest request, Exception e) {
        auditRepository.save(AuditLog.failure(request, e.getMessage()));
        // 독립 TX로 커밋 → 부모 TX 롤백과 무관하게 저장됨
    }
}
```

#### 사례 2: 배치 처리 중 개별 건 커밋

대량 데이터를 처리할 때, 일부 건이 실패해도 **성공한 건은 커밋을 유지**해야 하는 경우.

```kotlin
@Service
class PayoutBatchService(
    private val payoutService: PayoutService
) {
    fun processBatch(payouts: List<PayoutRequest>) {
        payouts.forEach { request ->
            try {
                payoutService.processSingle(request)  // REQUIRES_NEW
            } catch (e: Exception) {
                log.error("Payout 실패: ${request.id}", e)
                // 실패한 건만 롤백, 나머지는 영향 없음
            }
        }
    }
}

@Service
class PayoutService(
    private val payoutRepository: PayoutRepository
) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun processSingle(request: PayoutRequest) {
        val payout = payoutRepository.findById(request.id)
        payout.process()
        payoutRepository.save(payout)
        // 개별 TX로 커밋 → 다른 건의 실패와 무관
    }
}
```

### 커넥션 풀 고갈 위험

`REQUIRES_NEW`는 **기존 TX의 커넥션을 유지한 채 새 커넥션을 추가로 획득**한다. 이로 인해 동시에 2개의 커넥션을 점유하게 되며, 중첩이 깊어질수록 커넥션 소비가 급증한다.

```
[Thread-1]
  outerMethod() - 커넥션 1 획득 (TX-1)
    └─ innerMethod() - 커넥션 2 획득 (TX-2, REQUIRES_NEW)
         └─ deepMethod() - 커넥션 3 획득 (TX-3, REQUIRES_NEW)
         
→ Thread 하나가 커넥션 3개를 동시에 점유!
```

**위험 시나리오:**
- HikariCP 기본 커넥션 풀 크기: 10
- 동시 요청 10개가 `outerMethod()` 진입 → 커넥션 10개 소진
- 각 요청이 `innerMethod()`에서 REQUIRES_NEW로 새 커넥션 요청 → **대기 → 데드락**
- 모든 쓰레드가 커넥션을 기다리며 교착 상태

**방지 대책:**
1. 커넥션 풀 크기를 **동시 쓰레드 수 × REQUIRES_NEW 깊이** 이상으로 설정
2. REQUIRES_NEW 중첩을 **최대 1단계**로 제한
3. `HikariCP`의 `connectionTimeout`을 적절히 설정하여 데드락 조기 탐지

### 대량 데이터 배치 시 REQUIRES_NEW로 부분 커밋

전체를 하나의 트랜잭션으로 처리하면 **메모리 부족, 긴 트랜잭션 유지, 장애 시 전체 재처리** 문제가 발생한다. REQUIRES_NEW로 부분 커밋하면 이를 해결할 수 있다.

```java
// 안티패턴: 전체를 하나의 TX로 처리
@Transactional
public void processAll(List<Record> records) {  // 100만 건
    for (Record record : records) {
        process(record);
        // 99만 건 처리 후 실패 → 전체 롤백 😱
    }
}

// 개선: Chunk 단위 REQUIRES_NEW로 부분 커밋
public void processAll(List<Record> records) {
    List<List<Record>> chunks = Lists.partition(records, 500);
    for (List<Record> chunk : chunks) {
        try {
            chunkProcessor.processChunk(chunk);  // REQUIRES_NEW
        } catch (Exception e) {
            log.error("Chunk 처리 실패, 다음 Chunk로 계속", e);
            // 실패한 Chunk만 롤백, 이전 성공 Chunk는 커밋 유지
        }
    }
}

@Transactional(propagation = Propagation.REQUIRES_NEW)
public void processChunk(List<Record> chunk) {
    for (Record record : chunk) {
        process(record);
    }
    // Chunk 단위 커밋 → 메모리 절약 + 장애 복구 용이
}
```

### 성능 개선 사례

건건이 커밋하는 방식에서 **Chunk 단위 REQUIRES_NEW로 변경하여 대폭 성능 개선**이 가능하다.

| 방식 | 10만 건 처리 시간 | 커밋 횟수 | 커넥션 사용 패턴 |
|---|---|---|---|
| 건건이 커밋 (REQUIRES_NEW per item) | ~70초 | 100,000회 | 매번 TX 생성/커밋 오버헤드 |
| Chunk 500건 단위 REQUIRES_NEW | ~10초 | 200회 | TX 오버헤드 대폭 감소 |

**성능 개선 원리:**
- 트랜잭션 시작/커밋에는 **고정 오버헤드**가 존재 (DB 락 획득, WAL 기록, fsync 등)
- 건건이 커밋하면 이 오버헤드가 건수만큼 반복
- Chunk 단위로 묶으면 오버헤드를 `건수/Chunk Size`로 줄일 수 있음
- Writer에서 **벌크 INSERT/UPDATE**를 사용하면 추가 성능 향상

```java
// JPA 벌크 INSERT를 위한 설정
// application.yml
spring:
  jpa:
    properties:
      hibernate:
        jdbc:
          batch_size: 500          # Chunk Size와 맞춤
          order_inserts: true       # INSERT 문 순서 정렬 → 벌크 효율 극대화
          order_updates: true
```

---

## 5. NESTED vs REQUIRES_NEW 비교

### 핵심 차이: 부모 트랜잭션과의 종속 관계

| 항목 | NESTED | REQUIRES_NEW |
|---|---|---|
| **구현 메커니즘** | SAVEPOINT | 완전히 별개의 물리 TX |
| **커넥션** | 부모와 **동일 커넥션** 사용 | **새 커넥션** 획득 |
| **부모 TX 롤백 시** | 자식도 함께 롤백 | 이미 커밋된 자식은 **유지** |
| **자식 TX 롤백 시** | SAVEPOINT까지만 롤백, 부모는 계속 진행 가능 | 자식만 롤백, 부모와 무관 |
| **커넥션 풀 부담** | 추가 커넥션 불필요 | 추가 커넥션 필요 (풀 고갈 위험) |
| **DB 드라이버 요구** | SAVEPOINT 지원 필요 | 특별한 요구 없음 |
| **JPA 호환성** | 제한적 (`JpaTransactionManager`에서 지원하지 않을 수 있음) | 완벽 호환 |

### 동작 비교 다이어그램

```
[NESTED]
TX-1 시작 ──────────────────────────────────── TX-1 커밋
    │                                            │
    ├── SAVEPOINT sp1 ──── 자식 성공 ────────────┤  (부모 TX에 포함)
    │                                            │
    └── SAVEPOINT sp2 ──── 자식 실패 → sp2 롤백 ─┘  (부모는 계속 진행)
    
    ※ TX-1이 롤백되면 → sp1, sp2 모두 롤백 (종속적)


[REQUIRES_NEW]
TX-1 시작 ── TX-1 일시중단 ─────────── TX-1 재개 ── TX-1 커밋
                  │                        │
                  └── TX-2 시작 ── TX-2 커밋 ┘  (완전 독립)
                  
    ※ TX-1이 롤백되어도 → TX-2 커밋은 유지 (독립적)
```

### 실무 선택 기준

```java
// NESTED가 적합한 경우:
// - 자식 실패 시 부분 롤백하되, 부모 롤백 시 전체 롤백이 필요한 경우
// - 추가 커넥션 사용을 피하고 싶을 때
// - 예: 주문 처리 중 부가 서비스(포인트 적립) 실패 시 부분 롤백
@Transactional
public void processOrder(Order order) {
    orderRepository.save(order);
    try {
        pointService.accumulatePoints(order);  // NESTED
    } catch (Exception e) {
        // 포인트 적립 실패해도 주문은 진행
        // 단, 주문 전체가 롤백되면 포인트도 롤백됨 (정합성 보장)
    }
}

// REQUIRES_NEW가 적합한 경우:
// - 부모 TX 결과와 무관하게 자식 커밋이 반드시 유지되어야 하는 경우
// - 예: 감사 로그, 실패 이력 기록
@Transactional
public void processPayment(Payment payment) {
    try {
        paymentGateway.execute(payment);
    } catch (Exception e) {
        auditService.logPaymentFailure(payment, e);  // REQUIRES_NEW
        // 결제 TX가 롤백되어도 실패 기록은 반드시 남음
        throw e;
    }
}
```

### NESTED 사용 시 주의사항

```java
// ⚠️ JPA(Hibernate)에서 NESTED 사용 시 주의
// JpaTransactionManager는 NESTED를 지원하지만,
// Hibernate의 flush 모드에 따라 예상과 다르게 동작할 수 있음

// SAVEPOINT 이전에 flush된 변경사항은 SAVEPOINT 롤백으로 되돌릴 수 없음
// → EntityManager의 1차 캐시와 DB 상태 불일치 발생 가능

// 권장: JPA 환경에서는 NESTED 대신 REQUIRES_NEW 사용
// JDBC 직접 사용 환경에서는 NESTED가 더 효율적 (커넥션 절약)
```

| 상황 | 권장 전파 속성 | 이유 |
|---|---|---|
| 감사 로그 / 실패 이력 기록 | REQUIRES_NEW | 부모 롤백과 무관하게 반드시 커밋 |
| 배치 처리 중 개별 건 독립 커밋 | REQUIRES_NEW | 실패 건과 성공 건 분리 |
| 부가 기능(알림 등) 부분 롤백 | NESTED | 커넥션 절약, 부모와 정합성 유지 |
| JPA 환경에서 부분 롤백 필요 | REQUIRES_NEW | NESTED의 JPA 호환성 제한 회피 |
| JDBC 환경에서 부분 롤백 필요 | NESTED | 동일 커넥션 사용으로 효율적 |

---

## 면접 예상 질문 정리

### Q1. Spring Batch에서 Chunk 처리 도중 실패하면 어떻게 되나요?
해당 Chunk의 트랜잭션만 롤백되고, 이전에 성공한 Chunk의 커밋은 유지됩니다. JobRepository에 저장된 메타데이터를 기반으로 마지막 성공 지점부터 재시작할 수 있습니다.

### Q2. Chunk Size를 어떻게 결정하나요?
커밋 오버헤드, 메모리 사용량, 실패 시 재처리 범위를 종합적으로 고려합니다. 일반적으로 100~500을 기준으로, 처리 시간이 긴 작업은 작게, 단순 INSERT 위주는 크게 설정합니다.

### Q3. REQUIRES_NEW를 사용할 때 가장 주의해야 할 점은?
커넥션 풀 고갈입니다. 기존 TX의 커넥션을 유지한 채 새 커넥션을 획득하므로, 동시 요청이 많을 때 데드락이 발생할 수 있습니다. 커넥션 풀 크기를 `동시 쓰레드 수 × REQUIRES_NEW 중첩 깊이` 이상으로 설정해야 합니다.

### Q4. NESTED와 REQUIRES_NEW의 차이를 설명해주세요.
NESTED는 SAVEPOINT를 사용하여 부모 TX 내에서 부분 롤백을 지원하며, 부모가 롤백되면 자식도 함께 롤백됩니다. REQUIRES_NEW는 완전히 독립된 TX를 생성하여 부모 롤백과 무관하게 자식 커밋이 유지됩니다. NESTED는 동일 커넥션을 사용하지만, REQUIRES_NEW는 새 커넥션이 필요합니다.

### Q5. 배치에서 건건이 커밋 vs Chunk 단위 커밋의 성능 차이는?
트랜잭션 시작/커밋에는 고정 오버헤드(DB 락, WAL 기록, fsync)가 있어서 건건이 커밋하면 이 오버헤드가 건수만큼 반복됩니다. Chunk 단위로 묶으면 커밋 횟수를 `건수/Chunk Size`로 줄여 수 배의 성능 개선이 가능합니다. 또한 벌크 INSERT/UPDATE를 활용할 수 있어 추가적인 I/O 절감 효과가 있습니다.

---

참고:
- [Spring Batch 공식 문서](https://docs.spring.io/spring-batch/reference/)
- [Spring Transaction Propagation - Baeldung](https://www.baeldung.com/spring-transactional-propagation-isolation)
- [Spring Docs - Transaction Propagation](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html)
