# TOCTOU (Time-of-Check-Time-of-Use)

## 정의

**TOCTOU**(Time-of-Check-Time-of-Use, 토크투)는 **검사 시점(Check)과 사용 시점(Use) 사이의 시간 간격**에서 상태가 변경되어 검사 결과가 무효화되는 경쟁 상태(Race Condition)의 한 유형이다.

```
스레드 A:  ── Check(조건 확인) ──────────────── Use(조건 기반 행동) ──
스레드 B:  ──────────────────── Modify(상태 변경) ──────────────────
                                ↑
                     이 틈에 상태가 바뀌어 Check 결과가 무효화됨
```

**핵심**: Check와 Use가 **원자적(atomic)으로 실행되지 않기 때문에** 발생한다.

---

## 발생 조건

TOCTOU가 발생하려면 다음 조건이 모두 충족되어야 한다:

1. **공유 자원에 대한 상태 확인(Check)** 이 먼저 수행됨
2. **확인 결과를 기반으로 행동(Use)** 을 수행함
3. Check와 Use 사이에 **다른 스레드/프로세스가 상태를 변경** 할 수 있음
4. Check-Use가 **하나의 원자적 연산으로 보호되지 않음**

---

## 분류: 어디서 발생하는가

### 1. 파일 시스템 TOCTOU (보안 취약점, CWE-367)

가장 고전적인 TOCTOU 사례. UNIX/Linux 보안에서 특히 중요하다.

```c
// 취약한 코드: setuid 프로그램에서 파일 접근 권한 확인
if (access("/tmp/data.txt", W_OK) == 0) {   // Check: 파일 쓰기 권한 확인
    // ← 이 틈에 공격자가 /tmp/data.txt를 /etc/passwd 심볼릭 링크로 교체
    fd = open("/tmp/data.txt", O_WRONLY);     // Use: 파일 열기 (실제로는 /etc/passwd)
    write(fd, data, len);                     // /etc/passwd에 쓰기 성공!
}
```

```
정상 사용자:  access("/tmp/data.txt") → OK ──────────────── open("/tmp/data.txt") → 쓰기
공격자:       ──────────────────────── symlink("/tmp/data.txt", "/etc/passwd") ────
```

**실제 영향**: 권한 상승(Privilege Escalation) 공격. 일반 사용자가 root 전용 파일을 수정할 수 있게 됨.

**해결**: `access()` + `open()` 대신 **`open()`으로 직접 열고 `fstat()`으로 검증** (열린 파일 디스크립터 기반)

---

### 2. 비즈니스 로직 TOCTOU (동시성 버그)

웹 서비스에서 더 흔하게 마주치는 유형이다.

#### 예시 A: 재고 차감

```kotlin
// 취약한 코드
fun purchase(productId: String, quantity: Int) {
    val stock = stockRepository.findById(productId)  // Check: 재고 확인
    if (stock.quantity >= quantity) {
        // ← 이 틈에 다른 요청이 재고를 차감
        stock.quantity -= quantity                     // Use: 재고 차감 → 음수 가능!
        stockRepository.save(stock)
    }
}
```

#### 예시 B: 잔액 차감 (금융)

```kotlin
// 취약한 코드
fun withdraw(walletId: String, amount: Long) {
    val wallet = walletRepository.findById(walletId)  // Check: 잔액 확인
    if (wallet.balance >= amount) {
        // ← 이 틈에 다른 요청이 잔액을 차감
        wallet.balance -= amount                       // Use: 잔액 차감 → 마이너스 가능!
        walletRepository.save(wallet)
    }
}
```

#### 예시 C: 멱등성 검사

```kotlin
// 취약한 코드
fun processPayment(transactionId: String, amount: Long) {
    if (!transactionRepository.existsById(transactionId)) {  // Check: 중복 확인
        // ← 이 틈에 동일 transactionId 요청이 Check를 통과
        processAndSave(transactionId, amount)                  // Use: 이중 처리 발생!
    }
}
```

---

### 3. 인증/권한 TOCTOU

```kotlin
// 취약한 코드
fun deleteUser(requesterId: String, targetUserId: String) {
    val requester = userRepository.findById(requesterId)
    if (requester.role == Role.ADMIN) {          // Check: 관리자 확인
        // ← 이 틈에 requester의 권한이 ADMIN → USER로 변경됨
        userRepository.delete(targetUserId)       // Use: 더 이상 관리자가 아닌데 삭제 수행
    }
}
```

---

## 해결 패턴

### 패턴 1: 원자적 연산 (Atomic Operation)

Check와 Use를 하나의 원자적 연산으로 합친다.

```sql
-- Check-then-Use 분리 (취약)
SELECT balance FROM wallets WHERE id = 'wallet-1';  -- Check
UPDATE wallets SET balance = balance - 10000 WHERE id = 'wallet-1';  -- Use

-- 원자적 연산 (안전)
UPDATE wallets SET balance = balance - 10000
WHERE id = 'wallet-1' AND balance >= 10000;  -- Check + Use 동시에
```

```kotlin
// JPA에서 @Query로 원자적 업데이트
@Modifying
@Query("UPDATE Wallet w SET w.balance = w.balance - :amount WHERE w.id = :id AND w.balance >= :amount")
fun atomicWithdraw(id: String, amount: Long): Int  // 반환값 0이면 잔액 부족
```

**장점**: 가장 단순하고 확실함
**단점**: 복잡한 비즈니스 로직(여러 테이블, 외부 API 호출 등)에는 적용하기 어려움

---

### 패턴 2: 비관적 락 (Pessimistic Lock)

Check 시점부터 Use 완료까지 다른 스레드의 접근을 차단한다.

```kotlin
@Transactional
fun withdraw(walletId: String, amount: Long) {
    // SELECT ... FOR UPDATE → 트랜잭션 끝날 때까지 다른 트랜잭션이 이 행을 수정 불가
    val wallet = walletRepository.findByIdWithLock(walletId)  // Check + Lock
    wallet.withdraw(amount)                                    // Use (락 보호 하에 실행)
    // 트랜잭션 커밋 시 락 해제
}
```

```
스레드 A:  ── Lock + Check ── Use ── Commit + Unlock ──
스레드 B:  ── Lock 시도 (대기) ─────────────────────── Lock + Check ── Use ── ...
```

**장점**: 확실한 보호, 복잡한 로직에도 적용 가능
**단점**: 락 대기로 인한 처리량 저하, 데드락 위험

---

### 패턴 3: 낙관적 락 (Optimistic Lock)

Use 시점에 Check 이후 상태가 변경되었는지 검증한다. 변경되었으면 재시도.

```kotlin
@Entity
class Wallet(
    @Id val id: String,
    var balance: Long,
    @Version var version: Long = 0,  // 버전 필드
)

@Transactional
fun withdraw(walletId: String, amount: Long) {
    val wallet = walletRepository.findById(walletId)  // Check (version=1 읽음)
    wallet.withdraw(amount)                            // Use
    walletRepository.save(wallet)
    // UPDATE ... SET balance=?, version=2 WHERE id=? AND version=1
    // → version이 달라졌으면 OptimisticLockException 발생
}
```

```
스레드 A:  ── Read(v=1) ── 수정 ── Save(v=1→2) 성공 ──
스레드 B:  ── Read(v=1) ── 수정 ── Save(v=1→2) 실패! (이미 v=2) → 재시도
```

**장점**: 락 대기 없음, 높은 처리량
**단점**: 충돌이 빈번하면 재시도 비용이 큼. 금융 도메인처럼 충돌이 잦은 경우 비관적 락이 유리

---

### 패턴 4: Double-Check Locking

성능과 안전성을 모두 확보하는 패턴. **락 바깥에서 빠른 1차 검사, 락 안에서 정확한 2차 검사**를 수행한다.

이 패턴은 멱등성 검사에서 특히 유용하다.

```kotlin
fun withdraw(walletId: String, amount: Long, transactionId: String): TransactionResult {
    // 1차 Check (락 없이, 빠른 경로)
    // → 대부분의 중복 요청을 여기서 걸러냄
    redis.get("idempotency:$transactionId")?.let {
        return cachedResult(transactionId)
    }

    // 락 획득 (신규 요청만 여기까지 도달)
    return doWithdraw(walletId, amount, transactionId)
}

@DistributedLock(key = "'wallet:lock:' + #walletId")
@Transactional
fun doWithdraw(walletId: String, amount: Long, transactionId: String): TransactionResult {
    // 2차 Check (락 내부, TOCTOU 방어)
    // → 1차 Check와 락 획득 사이에 다른 스레드가 처리한 경우를 잡음
    transactionRepository.findByTransactionId(transactionId)?.let {
        redis.set("idempotency:$transactionId", "1", 24.hours)
        return TransactionResult.from(it)
    }

    // Use (안전하게 실행)
    val wallet = walletRepository.findByIdWithLock(walletId)
    wallet.withdraw(amount)
    ...
}
```

```
요청 A (txn-1):  ── 1차 Check(미스) ── 락 획득 ── 2차 Check(미스) ── 출금 ── 캐시 등록 ── 락 해제
요청 B (txn-1):  ── 1차 Check(히트!) ── 즉시 반환 (락 불필요)
요청 C (txn-1):  ── 1차 Check(미스, A가 캐시 등록 전) ── 락 대기 ── 락 획득 ── 2차 Check(히트!) ── 반환
                                                                                    ↑ TOCTOU 방어
```

**장점**: 대부분의 중복 요청이 락 없이 빠르게 반환됨 + TOCTOU 안전
**단점**: 코드가 다소 복잡

---

### 패턴 5: CAS (Compare-And-Swap)

하드웨어/소프트웨어 레벨의 원자적 비교-교환 연산.

```kotlin
// Java AtomicLong 예시
val balance = AtomicLong(100_000)

fun withdraw(amount: Long): Boolean {
    while (true) {
        val current = balance.get()           // Check
        if (current < amount) return false
        val next = current - amount
        if (balance.compareAndSet(current, next)) {  // Check + Use 원자적
            return true  // 성공
        }
        // current가 변경되었으면 루프 재시도
    }
}
```

**장점**: 락 없이 원자적 연산, 매우 빠름
**단점**: 단일 변수에만 적용 가능. DB나 복수 자원에는 사용 불가

---

## 패턴 선택 가이드

| 상황 | 권장 패턴 | 이유 |
|------|----------|------|
| 단일 DB 컬럼 업데이트 | 원자적 연산 | 가장 단순하고 확실 |
| 금융/재고 (충돌 빈번) | 비관적 락 | 재시도 비용 > 락 대기 비용 |
| 일반 CRUD (충돌 드묾) | 낙관적 락 | 락 오버헤드 없이 높은 처리량 |
| 멱등성 + 성능 동시 필요 | Double-Check Locking | 빠른 경로 + TOCTOU 방어 |
| 단일 변수, 극한 성능 | CAS | 락 없는 원자적 연산 |
| 분산 환경 (멀티 인스턴스) | 분산 락(Redis) + DB 락 | 인스턴스 간 동시성 제어 |

---

## TOCTOU vs 일반 Race Condition

| 구분 | 일반 Race Condition | TOCTOU |
|------|--------------------|----|
| 핵심 | 공유 자원에 대한 비원자적 접근 | **조건 검사**와 **행동** 사이의 간격 |
| 패턴 | Read-Modify-Write | **Check-then-Use** |
| 예시 | `i++` (Read → Modify → Write) | `if (exists) → use` |
| 특징 | 값의 무결성 문제 | 조건의 무효화 문제 |
| 보안 관점 | 데이터 손상 | 권한 우회, 이중 처리 등 |

TOCTOU는 Race Condition의 **하위 분류**이다. 모든 TOCTOU는 Race Condition이지만, 모든 Race Condition이 TOCTOU는 아니다.

---

## 실무에서 자주 만나는 TOCTOU 체크리스트

코드 리뷰 시 아래 패턴이 보이면 TOCTOU 가능성을 의심할 것:

- [ ] `if (존재 여부 확인) → 생성/삭제` 패턴
- [ ] `if (잔액/재고 확인) → 차감` 패턴
- [ ] `if (권한 확인) → 실행` 패턴
- [ ] `if (락 상태 확인) → 진입` 패턴
- [ ] `if (파일 존재 확인) → 파일 열기` 패턴
- [ ] `if (중복 확인) → 저장` 패턴 (멱등성)

**검증 질문**: "Check와 Use 사이에 다른 스레드가 상태를 바꾸면 어떻게 되는가?"

---

## 참고

- [CWE-367: Time-of-check Time-of-use (TOCTOU) Race Condition](https://cwe.mitre.org/data/definitions/367.html)
- [Wikipedia: Time-of-check to time-of-use](https://en.wikipedia.org/wiki/Time-of-check_to_time-of-use)
- [OWASP: Race Conditions](https://owasp.org/www-community/vulnerabilities/Race_Conditions)
