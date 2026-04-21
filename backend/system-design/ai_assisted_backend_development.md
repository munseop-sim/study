# AI 보조 백엔드 개발: 시니어 개발자의 역할과 검증 체계

> 최종 수정: 2026-04-21

---

## 1. AI 코드 생성의 한계

AI는 **패턴 기반 코드 생성**에는 능하지만, 다음 영역은 구조적으로 취약하다.

| 영역 | AI의 한계 | 이유 |
|------|-----------|------|
| 트랜잭션 경계 | 메서드 하나에 `@Transactional` 추가하는 수준 | 분산 트랜잭션, 2PC 맥락 파악 불가 |
| 동시성 | Race condition 탐지 불가 | 런타임 인터리빙 예측 불가 |
| 예외 처리 | happy path 위주 생성 | 도메인 예외 계층 모름 |
| DB 제약 | nullable, unique 누락 빈번 | 기존 스키마 전체 맥락 없음 |
| 로깅 | 로그가 없거나 과잉 | 운영 맥락(PII, 로그 레벨 전략) 모름 |
| 테스트 | 성공 케이스만 작성 | 실패 경로, edge case 식별 어려움 |

**핵심 교훈**: AI가 생성한 코드는 "초안"이다. 컴파일되고 테스트가 통과해도 운영 안전하다는 의미가 아니다.

---

## 2. 반드시 직접 검토할 6대 영역

### 2-1. 트랜잭션 / 동시성

```java
// AI가 생성한 코드 (위험)
@Transactional
public void transfer(Long fromId, Long toId, BigDecimal amount) {
    Account from = accountRepo.findById(fromId).orElseThrow();
    Account to = accountRepo.findById(toId).orElseThrow();
    from.deduct(amount);  // <-- 동시 요청 시 Lost Update
    to.add(amount);
    accountRepo.save(from);
    accountRepo.save(to);
}

// 시니어가 수정한 코드
@Transactional
public void transfer(Long fromId, Long toId, BigDecimal amount) {
    // 데드락 방지: 항상 작은 ID 먼저 락
    Long firstId = Math.min(fromId, toId);
    Long secondId = Math.max(fromId, toId);

    Account first = accountRepo.findByIdWithLock(firstId);  // SELECT FOR UPDATE
    Account second = accountRepo.findByIdWithLock(secondId);

    Account from = fromId.equals(firstId) ? first : second;
    Account to = toId.equals(firstId) ? first : second;

    from.deduct(amount);
    to.add(amount);
    // dirty checking으로 자동 저장, 명시적 save 불필요
}
```

**체크리스트**
- [ ] 낙관적 락 vs 비관적 락 선택이 적절한가?
- [ ] `@Transactional(readOnly = true)` 누락은 없는가?
- [ ] 트랜잭션 내에서 외부 API 호출하는 패턴은 없는가?
- [ ] 분산 환경에서 동일 데이터에 동시 접근하는 경로가 있는가?

### 2-2. 예외 처리

```kotlin
// AI가 생성한 코드 (위험)
fun processPayment(request: PaymentRequest): PaymentResult {
    val account = accountRepo.findById(request.accountId)
        ?: throw RuntimeException("계정 없음")  // 범용 예외 사용
    // 잔액 부족 케이스 처리 없음
    return paymentGateway.charge(account, request.amount)
}

// 시니어가 수정한 코드
fun processPayment(request: PaymentRequest): PaymentResult {
    val account = accountRepo.findById(request.accountId)
        ?: throw AccountNotFoundException(request.accountId)  // 도메인 예외

    if (account.balance < request.amount) {
        throw InsufficientBalanceException(
            accountId = request.accountId,
            required = request.amount,
            available = account.balance
        )
    }

    return try {
        paymentGateway.charge(account, request.amount)
    } catch (e: PaymentGatewayException) {
        // 재시도 가능 여부를 예외에 담아 상위에서 판단
        throw PaymentProcessingException(
            orderId = request.orderId,
            retryable = e.isTransient,
            cause = e
        )
    }
}
```

### 2-3. DB 제약 조건

```sql
-- AI가 생성한 DDL (위험)
CREATE TABLE remittance (
    id BIGINT PRIMARY KEY,
    order_id VARCHAR(255),       -- UNIQUE 누락
    amount DECIMAL(10, 2),       -- precision 부족
    status VARCHAR(50),          -- CHECK 제약 없음
    created_at TIMESTAMP
);

-- 시니어가 수정한 DDL
CREATE TABLE remittance (
    id          BIGINT          NOT NULL,
    order_id    VARCHAR(255)    NOT NULL UNIQUE,        -- 멱등성 보장
    amount      DECIMAL(19, 4)  NOT NULL CHECK (amount > 0),
    status      VARCHAR(50)     NOT NULL
                CHECK (status IN ('PENDING','PROCESSING','COMPLETED','FAILED')),
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    version     BIGINT          NOT NULL DEFAULT 0,     -- 낙관적 락
    CONSTRAINT pk_remittance PRIMARY KEY (id)
);

CREATE INDEX idx_remittance_status_created ON remittance(status, created_at)
    WHERE status IN ('PENDING', 'PROCESSING');          -- 부분 인덱스
```

### 2-4. 로깅

```kotlin
// AI가 생성한 코드 (위험)
fun approveRemittance(remittanceId: Long, userId: Long) {
    logger.info("Approving remittance $remittanceId for user $userId")  // PII 유출 가능
    // 중간 단계 로그 없음
    remittanceService.approve(remittanceId)
    logger.info("Done")  // 결과, 소요시간 없음
}

// 시니어가 수정한 코드
fun approveRemittance(remittanceId: Long, userId: Long) {
    val startTime = System.currentTimeMillis()
    logger.info("송금 승인 요청: remittanceId={}", remittanceId)  // userId 제외 (PII)

    val result = remittanceService.approve(remittanceId)

    val elapsed = System.currentTimeMillis() - startTime
    logger.info(
        "송금 승인 완료: remittanceId={}, status={}, elapsedMs={}",
        remittanceId, result.status, elapsed
    )
    // 실패 시 WARN/ERROR 레벨, 재시도 정보 포함
}
```

### 2-5. 테스트

```kotlin
// AI가 생성한 테스트 (불완전)
@Test
fun `송금 성공 테스트`() {
    val result = transferService.transfer(1L, 2L, BigDecimal("10000"))
    assertThat(result.status).isEqualTo(TransferStatus.SUCCESS)
}

// 시니어가 보완한 테스트
class TransferServiceTest : FunSpec({
    test("정상 송금 성공") { /* ... */ }

    test("잔액 부족 시 InsufficientBalanceException 발생") {
        val from = accountFixture(balance = BigDecimal("5000"))
        shouldThrow<InsufficientBalanceException> {
            transferService.transfer(from.id, toId, BigDecimal("10000"))
        }
    }

    test("동시 송금 요청 시 최종 잔액 일관성 보장") {
        val account = accountFixture(balance = BigDecimal("100000"))
        val latch = CountDownLatch(10)
        val errors = ConcurrentLinkedQueue<Exception>()

        repeat(10) {
            thread {
                try {
                    transferService.transfer(account.id, otherId, BigDecimal("10000"))
                } catch (e: Exception) {
                    errors.add(e)
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await()
        val finalAccount = accountRepo.findById(account.id)!!
        assertThat(finalAccount.balance).isGreaterThanOrEqualTo(BigDecimal.ZERO)
    }

    test("송금 실패 시 잔액 롤백 검증") { /* ... */ }
    test("멱등성: 같은 orderId 재요청 시 중복 처리 안 됨") { /* ... */ }
})
```

---

## 3. 시니어 개발자의 역할 재정의

AI 도입 이후 시니어의 역할은 "코드를 빨리 짜는 것"에서 다음으로 이동한다.

```
Before AI:  [요구사항 분석] → [설계] → [코딩(70%)] → [리뷰] → [테스트]
After AI:   [요구사항 분석] → [설계(강화)] → [AI 코드 검증(70%)] → [리뷰] → [테스트]
```

**시니어가 집중해야 할 영역**
1. **도메인 모델 설계**: AI는 도메인 지식 없이 CRUD를 생성함. 불변 조건, 도메인 규칙 설계는 사람이 해야 함
2. **운영 장애 경험 적용**: AI는 "이게 프로덕션에서 새벽 3시에 어떻게 터질지" 모름
3. **비기능 요건 판단**: 성능, 확장성, 보안 트레이드오프는 시스템 전체 맥락이 필요
4. **코드 리뷰 품질 향상**: AI 생성 코드의 위험 패턴을 탐지하는 리뷰 역량

---

## 4. 금융 도메인 위험 패턴

### Long overflow

```java
// 위험: 큰 금액에서 overflow 발생 가능
long totalAmount = amount1 + amount2;  // Long.MAX_VALUE = 9,223,372,036,854,775,807

// 안전:
BigDecimal totalAmount = amount1.add(amount2);

// Long 사용 시에도 검증 필요
long safeAdd(long a, long b) {
    long result = Math.addExact(a, b); // overflow 시 ArithmeticException
    return result;
}
```

### 멱등성 누락

```kotlin
// 위험: 네트워크 재시도 시 이중 처리
@PostMapping("/remittance")
fun createRemittance(@RequestBody request: RemittanceRequest): ResponseEntity<*> {
    val remittance = remittanceService.create(request)  // 재시도마다 중복 생성
    return ResponseEntity.ok(remittance)
}

// 안전: Idempotency Key 활용
@PostMapping("/remittance")
fun createRemittance(
    @RequestHeader("Idempotency-Key") idempotencyKey: String,
    @RequestBody request: RemittanceRequest
): ResponseEntity<*> {
    val remittance = remittanceService.createOrGet(idempotencyKey, request)
    return ResponseEntity.ok(remittance)
}

// 서비스 레이어
fun createOrGet(idempotencyKey: String, request: RemittanceRequest): Remittance {
    return remittanceRepo.findByIdempotencyKey(idempotencyKey)
        ?: remittanceRepo.save(
            Remittance.create(idempotencyKey, request)
        )
}
```

### 상태 전이 검증 누락

```kotlin
// 위험: 어떤 상태에서든 취소 가능
fun cancelRemittance(id: Long) {
    val remittance = remittanceRepo.findById(id)!!
    remittance.status = RemittanceStatus.CANCELLED  // COMPLETED 상태에서도 취소??
    remittanceRepo.save(remittance)
}

// 안전: 도메인 객체에서 상태 전이 규칙 관리
data class Remittance(
    val status: RemittanceStatus,
    // ...
) {
    fun cancel(): Remittance {
        check(status in setOf(PENDING, PROCESSING)) {
            "취소 불가능한 상태: $status"
        }
        return copy(status = CANCELLED)
    }
}
```

---

## 5. AI 코드 리뷰 체크리스트

```
[ ] 트랜잭션 경계가 올바른가? (너무 넓거나 좁은 @Transactional)
[ ] 동시성 문제가 없는가? (Race condition, Lost Update)
[ ] 예외 계층이 도메인에 맞게 정의됐는가?
[ ] DB 제약조건이 충분한가? (NOT NULL, UNIQUE, CHECK)
[ ] 로깅에 PII(개인정보)가 포함되지 않았는가?
[ ] 실패 케이스 테스트가 작성됐는가?
[ ] 멱등성이 보장되는가?
[ ] 금액 연산에 BigDecimal을 쓰는가?
[ ] 상태 전이 규칙이 도메인 객체에 캡슐화됐는가?
[ ] 외부 API 호출 타임아웃이 설정됐는가?
```

---

## 6. 면접 포인트

**Q1. AI 코드 생성 도구를 어떻게 활용하시나요?**
> 반복적인 보일러플레이트(DTO, 테스트 픽스처, 쿼리 메서드)는 AI에게 초안을 맡깁니다. 하지만 트랜잭션 경계, 동시성, 예외 설계, DB 스키마는 반드시 직접 검토합니다. AI가 생성한 코드를 "컴파일 되면 OK"로 보지 않고 6대 영역 체크리스트로 리뷰합니다.

**Q2. AI 시대에 시니어 개발자의 가치는 무엇인가요?**
> 코딩 속도보다 도메인 모델 설계, 운영 장애 경험, 비기능 요건 판단 역량이 더 중요해집니다. AI가 생성한 코드의 위험 패턴을 탐지하고 교정하는 능력, 그리고 팀 전체의 AI 활용 수준을 높이는 역할이 시니어의 핵심 가치입니다.

**Q3. 금융 서비스에서 AI 코드를 프로덕션에 올릴 때 가장 주의할 점은?**
> 멱등성과 동시성입니다. 네트워크 재시도나 중복 요청으로 인한 이중 송금은 치명적이며 AI가 멱등성을 자동으로 구현하지 않습니다. 또한 동시 잔액 차감 같은 경쟁 조건은 테스트 환경에서 발견되지 않다가 프로덕션에서 터지기 때문에 코드 리뷰 단계에서 반드시 식별해야 합니다.
