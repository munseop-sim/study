# MySQL InnoDB 락 메커니즘 — Gap Lock, Next-Key Lock, Phantom Read

## 1. MVCC(Multi-Version Concurrency Control) 동작 원리

InnoDB는 MVCC를 통해 읽기 작업이 쓰기 작업을 블로킹하지 않도록 한다.

### Undo Log와 스냅샷

모든 변경 시 이전 버전 데이터를 **Undo Log**에 보관한다.
트랜잭션 시작 시 `read_view`(스냅샷)를 생성하고, 이 스냅샷 기준으로 데이터를 읽는다.

```
트랜잭션 A (T=100) 시작
  └─ read_view: "T=100 이전에 커밋된 데이터만 보겠다"

트랜잭션 B (T=101) 이후 INSERT/UPDATE
  └─ A는 B의 변경사항이 보이지 않음 (스냅샷 이후이므로)
  └─ Undo Log에서 이전 버전 데이터를 재구성하여 반환
```

이 메커니즘 덕분에 일반 SELECT는 다른 트랜잭션의 쓰기를 기다리지 않고 일관된 스냅샷을 읽는다.

---

## 2. REPEATABLE_READ에서의 Phantom Read

MySQL InnoDB의 기본 격리 수준은 `REPEATABLE_READ`다.

### 2.1 일반 SELECT — 스냅샷 읽기

```sql
-- 트랜잭션 A
BEGIN;
SELECT * FROM wallet WHERE user_id = 1 AND balance > 0;
-- 결과: 3건

-- 트랜잭션 B (A 진행 중)
INSERT INTO wallet (user_id, balance) VALUES (1, 5000);
COMMIT;

-- 트랜잭션 A에서 다시 조회
SELECT * FROM wallet WHERE user_id = 1 AND balance > 0;
-- 결과: 여전히 3건 (스냅샷 읽기이므로 Phantom Read 없음!)
```

일반 SELECT는 트랜잭션 시작 시점의 스냅샷을 읽으므로 Phantom Read가 발생하지 않는다.

### 2.2 잠금 읽기(Locking Read) — 현재 읽기(Current Read)

```sql
-- 트랜잭션 A
BEGIN;
SELECT * FROM wallet WHERE user_id = 1 AND balance > 0 FOR UPDATE;
-- 결과: 3건 (현재 읽기)

-- 트랜잭션 B
INSERT INTO wallet (user_id, balance) VALUES (1, 5000);
COMMIT;

-- 트랜잭션 A에서 다시 잠금 읽기
SELECT * FROM wallet WHERE user_id = 1 AND balance > 0 FOR UPDATE;
-- 결과: 4건! → Phantom Read 발생 가능
```

`SELECT FOR UPDATE`, `SELECT LOCK IN SHARE MODE`, `UPDATE`, `DELETE`는 **현재 읽기(Current Read)** 로, Undo Log가 아닌 실제 최신 데이터를 읽는다.

---

## 3. Record Lock, Gap Lock, Next-Key Lock

InnoDB는 Phantom Read를 방지하기 위해 범위 락(Range Lock)을 사용한다.

### 3.1 Record Lock (레코드 락)

**정의**: 인덱스 레코드 자체에 걸리는 락
**범위**: 특정 행 하나

```
[비유: 특정 방 문에 자물쇠를 거는 것]

인덱스: ... 10 ... 20 ... 30 ...
                ↑
         이 레코드만 락
```

```sql
-- id = 5인 레코드에 Record Lock
SELECT * FROM orders WHERE id = 5 FOR UPDATE;
```

### 3.2 Gap Lock (갭 락)

**정의**: 인덱스 레코드 사이의 간격(Gap)에 걸리는 락. 레코드 자체는 포함하지 않는다.
**목적**: 해당 간격에 새 레코드가 INSERT되는 것을 막아 Phantom Read 방지

```
[비유: 방과 방 사이 복도에 장벽을 치는 것]

인덱스: ... 10 ... 20 ... 30 ...
              ↑↑↑↑↑↑↑↑↑
        이 간격에 삽입 불가 (10과 20 사이)
```

```sql
-- balance 컬럼에 인덱스 있다고 가정
-- balance between 1000 AND 5000 범위 조회
SELECT * FROM wallet WHERE balance BETWEEN 1000 AND 5000 FOR UPDATE;
-- Gap Lock: (1000, 5000) 범위에 새 INSERT 차단
```

Gap Lock은 **동시성을 낮추는 대신 Phantom Read를 방지**한다.

### 3.3 Next-Key Lock

**정의**: Record Lock + 그 앞의 Gap Lock을 합친 것
**범위**: `(이전 레코드, 현재 레코드]` (좌개방, 우폐쇄)

InnoDB가 `SELECT FOR UPDATE`로 범위 스캔 시 기본적으로 Next-Key Lock을 사용한다.

```
인덱스: 10, 20, 30, 40이 존재할 때

Next-Key Lock 범위:
(-∞, 10]  (10, 20]  (20, 30]  (30, 40]  (40, +∞)
```

```sql
-- balance >= 1000 인 레코드 잠금
SELECT * FROM wallet WHERE balance >= 1000 FOR UPDATE;

-- 1000 이상의 모든 레코드에 Record Lock
-- 각 레코드 앞의 Gap에 Gap Lock
-- 마지막 레코드 이후에도 Gap Lock (supremum pseudo-record까지)
```

### 3.4 인덱스 존재 여부에 따른 차이

**인덱스가 있을 때**: 조건에 맞는 인덱스 범위만 잠금

```sql
-- user_id에 인덱스 있음
SELECT * FROM orders WHERE user_id = 100 FOR UPDATE;
-- user_id = 100인 레코드들과 그 사이 갭만 잠금
```

**인덱스가 없을 때**: 테이블 풀스캔 → **테이블 전체 잠금**

```sql
-- status에 인덱스 없음
SELECT * FROM orders WHERE status = 'PENDING' FOR UPDATE;
-- 테이블 전체에 락 → 다른 INSERT/UPDATE 모두 블로킹!
```

**결론**: `SELECT FOR UPDATE` 사용 시 WHERE 조건 컬럼에 인덱스가 없으면 매우 위험하다.

---

## 4. @Lock(PESSIMISTIC_WRITE) 실제 동작 (Spring Data JPA)

### 4.1 SQL 변환

```kotlin
// Kotlin + Spring Data JPA
interface WalletRepository : JpaRepository<Wallet, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.userId = :userId")
    fun findByUserIdForUpdate(@Param("userId") userId: Long): Wallet?
}
```

위 코드는 다음 SQL로 변환된다:

```sql
SELECT w.* FROM wallet w WHERE w.user_id = ? FOR UPDATE
```

### 4.2 실제 잠금 범위

```kotlin
// 예시: 포인트 차감
@Transactional
fun deductPoint(userId: Long, amount: BigDecimal) {
    val wallet = walletRepository.findByUserIdForUpdate(userId)
        ?: throw WalletNotFoundException(userId)

    wallet.deductBalance(amount)  // 잔액 검증 + 차감
    walletRepository.save(wallet)
}
```

- `user_id` 컬럼에 인덱스가 있으면: 해당 user의 레코드 + 그 갭만 잠금 (좁은 범위)
- `user_id` 컬럼에 인덱스가 없으면: 테이블 전체 잠금 → 다른 사용자의 출금도 블로킹

### 4.3 데드락 시나리오

```
트랜잭션 A: SELECT wallet WHERE id = 1 FOR UPDATE (← id=1 잠금 획득)
트랜잭션 B: SELECT wallet WHERE id = 2 FOR UPDATE (← id=2 잠금 획득)

트랜잭션 A: SELECT wallet WHERE id = 2 FOR UPDATE (← id=2 대기)
트랜잭션 B: SELECT wallet WHERE id = 1 FOR UPDATE (← id=1 대기)

→ 데드락! InnoDB가 둘 중 하나를 롤백
```

**데드락 방지책**

1. **일관된 잠금 순서**: 여러 행을 잠글 때 항상 같은 순서 (예: id 오름차순)
2. **잠금 범위 최소화**: 필요한 행만 잠금, 필요한 컬럼에만 인덱스
3. **트랜잭션 시간 단축**: 잠금 보유 시간 최소화
4. **낙관적 잠금 검토**: 충돌이 드문 경우 `@Version` 사용

```kotlin
// 낙관적 잠금 예시
@Entity
class Wallet {
    @Version
    var version: Long = 0
    // 충돌 시 OptimisticLockException 발생 → 재시도 로직 필요
}
```

---

## 5. 실무 사례

### 5.1 재고 차감 (e-commerce)

```sql
-- 재고 차감 시 동시 요청으로 인한 overselling 방지
SELECT stock FROM product WHERE id = ? FOR UPDATE;
UPDATE product SET stock = stock - ? WHERE id = ? AND stock >= ?;
-- 영향받은 행 = 0이면 재고 부족
```

### 5.2 포인트 차감 (결제)

```kotlin
@Transactional
fun usePoint(userId: Long, point: Long): PointUseResult {
    val userPoint = pointRepository.findByUserIdForUpdate(userId)
        ?: return PointUseResult.NOT_FOUND

    if (userPoint.available < point) {
        return PointUseResult.INSUFFICIENT
    }

    userPoint.use(point)
    pointRepository.save(userPoint)
    return PointUseResult.SUCCESS
}
```

### 5.3 잔액 출금 (금융)

```kotlin
@Transactional
fun withdraw(walletId: Long, amount: BigDecimal): WithdrawResult {
    // 비관적 잠금으로 동시 출금 방지
    val wallet = walletRepository.findByIdForUpdate(walletId)
        ?: throw WalletNotFoundException(walletId)

    wallet.withdraw(amount)  // 내부에서 잔액 검증
    walletRepository.save(wallet)

    // 출금 이력 저장 (Outbox와 연계)
    outboxRepository.save(WithdrawCompletedEvent(walletId, amount))

    return WithdrawResult.SUCCESS
}
```

---

## 6. SHOW ENGINE INNODB STATUS로 락 확인

```sql
-- 현재 잠금 대기 상황 확인
SHOW ENGINE INNODB STATUS;

-- 또는 performance_schema 활용 (MySQL 8.0)
SELECT
    r.trx_id         AS waiting_trx_id,
    r.trx_query      AS waiting_query,
    b.trx_id         AS blocking_trx_id,
    b.trx_query      AS blocking_query
FROM information_schema.innodb_lock_waits w
JOIN information_schema.innodb_trx b ON b.trx_id = w.blocking_trx_id
JOIN information_schema.innodb_trx r ON r.trx_id = w.requesting_trx_id;
```

데드락 발생 시 INNODB STATUS의 `LATEST DETECTED DEADLOCK` 섹션에서 관련 트랜잭션과 락 정보를 확인할 수 있다.

```sql
-- 현재 잠금 보유 현황 (MySQL 8.0)
SELECT * FROM performance_schema.data_locks;
SELECT * FROM performance_schema.data_lock_waits;
```

---

## 7. 락 종류 요약

| 락 종류 | 잠금 대상 | 목적 |
|---|---|---|
| Record Lock | 인덱스 레코드 자체 | 특정 행 수정 방지 |
| Gap Lock | 레코드 사이의 간격 | 범위 내 INSERT 방지 (Phantom 방지) |
| Next-Key Lock | 레코드 + 앞쪽 갭 | Record Lock + Gap Lock 조합 |
| Table Lock | 테이블 전체 | DDL 또는 전체 잠금 (LOCK TABLES) |

---

## 면접 포인트

### Q1. "REPEATABLE_READ에서 Phantom Read가 발생하나요?"
- 일반 SELECT(스냅샷 읽기)에서는 Phantom Read가 발생하지 않는다. 트랜잭션 시작 시점의 스냅샷을 읽기 때문이다.
- 그러나 `SELECT FOR UPDATE`(현재 읽기)를 사용하면 최신 데이터를 읽으므로 Phantom Read가 발생할 수 있다.
- InnoDB는 Next-Key Lock으로 이를 방지한다.

### Q2. "Gap Lock이 무엇이고 왜 필요한가요?"
- Gap Lock은 인덱스 레코드 사이의 간격에 걸리는 락이다.
- 해당 범위에 새 레코드가 INSERT되는 것을 막아 Phantom Read를 방지한다.
- 예: `WHERE id BETWEEN 10 AND 20 FOR UPDATE`이면 10~20 사이 갭에 Lock이 걸려 다른 트랜잭션이 그 사이 값을 INSERT할 수 없다.

### Q3. "인덱스가 없을 때 SELECT FOR UPDATE는 어떻게 동작하나요?"
- 인덱스가 없으면 풀 테이블 스캔이 발생하고, InnoDB는 테이블 전체에 Next-Key Lock을 건다.
- 이는 다른 모든 INSERT/UPDATE를 블로킹하므로 처리량이 급감한다.
- 따라서 SELECT FOR UPDATE 사용 컬럼에는 반드시 인덱스가 있어야 한다.

### Q4. "데드락이 발생하는 원인과 방지 방법은?"
- 두 트랜잭션이 서로 상대방이 보유한 락을 기다릴 때 발생한다.
- 방지: 여러 행을 잠글 때 항상 일관된 순서(예: id 오름차순)로 잠금. 잠금 보유 시간 최소화. 필요 시 낙관적 잠금 사용.
- InnoDB는 데드락 감지 후 한 트랜잭션을 자동 롤백하므로 애플리케이션에서 재시도 로직이 필요하다.

### Q5. "비관적 잠금 vs 낙관적 잠금을 어떤 기준으로 선택하나요?"
- 충돌 빈도가 높고 데이터 정합성이 매우 중요한 경우 (잔액 차감, 재고 차감): 비관적 잠금.
- 충돌 빈도가 낮고 읽기가 많은 경우 (설정 변경 등): 낙관적 잠금 (`@Version`).
- 낙관적 잠금은 재시도 비용이 있으므로 충돌이 잦으면 오히려 성능이 나빠질 수 있다.

### Q6. "잔액 출금 시 동시 요청이 들어오면 어떻게 처리하나요?"
- `SELECT FOR UPDATE`로 해당 지갑 행에 비관적 잠금을 건다.
- 동시에 여러 출금 요청이 들어와도 순차적으로 처리된다.
- 잔액 검증과 차감이 같은 트랜잭션 안에서 이루어지므로 overdraft(잔액 초과 출금)가 방지된다.
