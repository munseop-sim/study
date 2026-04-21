# Spring Transaction

## 스프링 트랜잭션 전파 속성

트랜잭션 경계에서 **이미 진행 중인 트랜잭션이 있을 때 / 없을 때** 어떻게 동작할지 결정하는 속성.
`@Transactional(propagation = ...)` 으로 설정.

| 속성 | 기존 트랜잭션 있음 | 기존 트랜잭션 없음 |
|---|---|---|
| **REQUIRED** (기본값) | 기존 트랜잭션 참여 | 새 트랜잭션 생성 |
| **REQUIRES_NEW** | 기존 트랜잭션 보류, 새 트랜잭션 생성 | 새 트랜잭션 생성 |
| **MANDATORY** | 기존 트랜잭션 참여 | 예외 발생 |
| **SUPPORTS** | 기존 트랜잭션 참여 | 트랜잭션 없이 실행 |
| **NOT_SUPPORTED** | 기존 트랜잭션 보류, 트랜잭션 없이 실행 | 트랜잭션 없이 실행 |
| **NESTED** | SAVEPOINT 생성 후 중첩 트랜잭션 시작 | 새 트랜잭션 생성 |
| **NEVER** | 예외 발생 | 트랜잭션 없이 실행 |

**주의사항**
- `REQUIRES_NEW`는 기존 트랜잭션을 보류하고 새 커넥션을 사용하므로 **커넥션 풀 고갈** 주의
- `NESTED`는 SAVEPOINT를 사용하므로 DB 드라이버 지원 여부 확인 필요 (JPA와 함께 사용 시 제한)
- 중첩 트랜잭션은 부모 트랜잭션에 종속: 중첩이 롤백돼도 부모는 커밋 가능, 부모가 롤백되면 중첩도 롤백

참고: [망나니개발자 - 스프링 트랜잭션 전파 속성](https://mangkyu.tistory.com/269)

---

## 스프링 트랜잭션 AOP 동작 흐름

### 핵심 3요소
1. **트랜잭션 AOP 프록시** — `@Transactional` 처리 주체
2. **트랜잭션 매니저(Transaction Manager)** — 트랜잭션의 실제 시작/종료 담당
3. **트랜잭션 동기화 매니저(Transaction Synchronization Manager)** — 커넥션 공유 담당

### 동작 흐름
1. 클라이언트가 트랜잭션 AOP 프록시 메서드 호출
2. 프록시가 트랜잭션 매니저를 획득하고 트랜잭션 시작 요청
3. 트랜잭션 매니저가 데이터소스에서 커넥션을 획득하고 트랜잭션 시작
4. 커넥션을 **트랜잭션 동기화 매니저**에 보관
5. 실제 비즈니스 로직 실행 (Repository 등에서 동기화 매니저의 커넥션을 꺼내 사용)
6. 트랜잭션 종료 시 동기화 매니저에서 커넥션을 꺼내 커밋/롤백 후 반환

**트랜잭션 매니저**: JDBC(`DataSourceTransactionManager`), JPA(`JpaTransactionManager`) 등 구현 세부를 추상화한 `PlatformTransactionManager` 인터페이스
**트랜잭션 동기화 매니저**: 트랜잭션이 걸친 여러 메서드 사이에서 **동일한 커넥션을 공유**하도록 도와주는 컴포넌트

---

## private 메서드에 @Transactional

**결론: private 메서드에 `@Transactional`을 선언해도 트랜잭션이 동작하지 않는다.**

### 이유: Spring AOP 프록시 방식의 한계

| 방식 | 프록시 생성 기준 | AOP 적용 가능 접근 제어자 |
|---|---|---|
| JDK Dynamic Proxy | 인터페이스 기반 | `public`만 가능 |
| CGLIB | 구체 클래스 상속 | `public`, `protected`, `package-private` 가능 (`private` 불가) |

두 방식 모두 `private` 메서드에는 AOP 적용 불가.

### Self-Invocation(자가 호출) 문제

같은 클래스 내부에서 `@Transactional` 메서드를 호출하면 **프록시를 거치지 않고 직접 호출**되므로 트랜잭션이 동작하지 않음.

```java
// 문제 상황: 내부 호출 - 트랜잭션 미동작
public void outerSave(Member member) {
    saveWithPublic(member); // this.saveWithPublic() 직접 호출 → 프록시 미경유
}

@Transactional
public void saveWithPublic(Member member) { ... }
```

**해결 방법**: 별도 클래스로 분리하여 외부에서 프록시를 통해 호출되도록 구조 변경

---

## 트랜잭션 예외 롤백

### Spring의 기본 롤백 규칙

| 예외 종류 | 기본 동작 | 이유 |
|---|---|---|
| **Checked Exception** | 롤백 **안 함** | 개발자가 예상하고 처리할 수 있는 정상적인 예외 상황으로 가정 |
| **Unchecked Exception (RuntimeException, Error)** | 롤백 **함** | 프로그래머 실수나 시스템 문제로 인한 회복 불가능한 상황으로 가정 |

### 커스터마이징
- `@Transactional(rollbackFor = SomeCheckedException.class)` → Checked Exception도 롤백 강제
- `@Transactional(noRollbackFor = SomeRuntimeException.class)` → Unchecked Exception도 롤백 방지

### Spring의 예외 통합 처리
JDBC, JPA, Hibernate 등 데이터 액세스 계층의 다양한 예외를 공통 Unchecked Exception인 **`DataAccessException`** 계층으로 변환 → 일관된 예외 처리 전략 수립 가능

참고: [Spring Docs - Rolling Back a Declarative Transaction](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/rolling-back.html)

---

## noRollbackFor 심층 이해

### 언제 쓰는가

비즈니스 로직에서 **예외를 던지면서도 일부 데이터를 커밋**해야 할 때 사용한다.

대표적 사례: 금융 시스템에서 실패 기록을 남겨야 하는 경우

```java
// 예: 주문 처리 서비스
@Transactional(noRollbackFor = OrderRejectedException.class)
public OrderResult processOrder(Order order) {
    try {
        validateStock(order);         // 재고 부족 시 OrderRejectedException
        deductStock(order);
        return OrderResult.success();
    } catch (OrderRejectedException e) {
        orderHistoryRepository.save(OrderHistory.rejected(order, e.getMessage()));  // 실패 이력 저장
        throw e;  // 예외를 다시 던짐 → 하지만 noRollbackFor로 커밋됨
    }
}
```

**noRollbackFor가 없으면:**
1. `OrderRejectedException`은 RuntimeException 하위
2. Spring이 RuntimeException 감지 → 롤백
3. `orderHistoryRepository.save()`로 저장한 실패 이력도 **함께 롤백** → 사라짐

**noRollbackFor가 있으면:**
1. 예외가 발생하지만 롤백하지 않고 **커밋**
2. 실패 이력이 DB에 남음
3. 예외는 그대로 호출자에게 전파 → Controller에서 에러 응답 반환

### noRollbackFor의 동작 조건: 최외곽 트랜잭션에서만 유효

```java
// ⚠️ 주의: noRollbackFor는 자신이 트랜잭션 경계의 "주인"일 때만 동작한다

// Case 1: 자신이 최외곽 트랜잭션 → ✅ noRollbackFor 정상 동작
// outerMethod()에 @Transactional이 없음
public void outerMethod() {
    innerService.doWork();  // @Transactional(noRollbackFor = BizException.class)
}

// Case 2: 부모 트랜잭션에 참여 → ❌ noRollbackFor 무효화
@Transactional
public void outerMethod() {
    innerService.doWork();  // REQUIRED → 부모 TX에 참여 → noRollbackFor 무시됨
}
```

**Case 2가 위험한 이유:**
- `innerService.doWork()`는 `REQUIRED`(기본값) → 부모 TX에 참여
- `BizException` 발생 → inner 메서드의 `noRollbackFor`는 자기 레벨에서만 적용
- 예외가 부모 메서드까지 전파 → 부모의 `@Transactional`에는 `noRollbackFor`가 없음
- Spring이 부모 TX를 **rollback-only로 마킹** → 전체 롤백
- inner에서 저장한 실패 기록도 모두 사라짐

```
Case 1 흐름 (정상):
  outerMethod() [TX 없음]
    └─ doWork() [TX 시작, noRollbackFor 유효]
        └─ BizException → 커밋 ✅

Case 2 흐름 (문제):
  outerMethod() [TX-0 시작]
    └─ doWork() [TX-0에 참여, noRollbackFor가 TX-0에 적용 안 됨]
        └─ BizException → TX-0 롤백 마킹 → 전체 롤백 💥
```

### noRollbackFor 대안: REQUIRES_NEW로 실패 기록 분리

`noRollbackFor`를 쓰지 않고 같은 효과를 내는 방법:

```java
@Transactional
public OrderResult processOrder(Order order) {
    try {
        validateStock(order);
        deductStock(order);
        return OrderResult.success();
    } catch (OrderRejectedException e) {
        // 실패 기록을 별도 트랜잭션으로 저장
        orderHistoryService.saveRejection(order, e.getMessage());  // REQUIRES_NEW
        throw e;  // 현재 TX는 롤백되지만, 실패 기록은 독립 커밋으로 살아남음
    }
}

@Service
public class OrderHistoryService {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveRejection(Order order, String reason) {
        orderHistoryRepository.save(OrderHistory.rejected(order, reason));
    }
}
```

| 방식 | 장점 | 단점 |
|------|------|------|
| **noRollbackFor** | 구조 단순, 하나의 TX에서 처리 | 최외곽 TX에서만 유효, 의도 파악 어려움 |
| **REQUIRES_NEW 분리** | 부모 TX 롤백과 독립적으로 동작 | 별도 클래스 필요 (self-invocation 문제), 추가 커넥션 사용 |

### rollback-only 마킹 문제

부모 TX에 참여한 내부 메서드에서 예외가 발생하면, 예외를 **catch해도** 롤백될 수 있다:

```java
@Transactional
public void parent() {
    try {
        childService.doSomething();  // REQUIRED → 부모 TX 참여
    } catch (RuntimeException e) {
        // catch했지만 이미 TX가 rollback-only로 마킹됨!
        log.warn("에러 발생, 무시하고 계속...");
    }
    // 여기서 다른 작업을 해도...
    // 메서드 종료 시 commit 시도 → UnexpectedRollbackException 발생!
}
```

**이유:** Spring의 `REQUIRED` 전파에서 내부 메서드 예외 시, 트랜잭션 매니저가 **전체 TX를 rollback-only로 마킹**. 외부에서 catch해도 마킹은 해제되지 않음. 커밋 시점에 `UnexpectedRollbackException` 발생.

**해결 방법:**
1. 내부 메서드를 `REQUIRES_NEW`로 분리 → 실패해도 부모 TX에 영향 없음
2. 내부 메서드를 `NESTED`로 설정 → SAVEPOINT로 부분 롤백 (JPA에서는 제한적)

---

## 트랜잭션 격리수준 (Transaction Isolation Level)

### 격리 수준별 이상 현상 비교

| 격리수준 | Dirty Read | Non-Repeatable Read | Phantom Read |
|---|---|---|---|
| READ UNCOMMITTED | O | O | O |
| READ COMMITTED | X | O | O |
| REPEATABLE READ | X | X | O (InnoDB는 갭락으로 방지) |
| SERIALIZABLE | X | X | X |

- **READ COMMITTED**: Oracle, PostgreSQL 기본값
- **REPEATABLE READ**: MySQL(InnoDB) 기본값
- **Dirty Read**: 커밋되지 않은 데이터를 읽어 롤백 시 불일치 발생
- **Non-Repeatable Read**: 같은 쿼리를 두 번 실행했을 때 다른 결과 (UPDATE/DELETE)
- **Phantom Read**: 같은 쿼리를 두 번 실행했을 때 없던 행이 나타남 (INSERT)

데이터 정합성과 성능은 반비례: 낮은 격리수준 = 높은 동시처리 / 높은 격리수준 = 높은 정합성

### 스냅샷 생성 시점 — DB 벤더별 차이

격리 수준의 실제 동작은 **DB 벤더마다 다르다**. 특히 스냅샷(consistent read) 시점이 핵심.

#### READ COMMITTED

```
모든 DB 공통: 각 SQL 문(statement)마다 최신 커밋된 데이터를 읽음

TX-A:  BEGIN → SELECT balance (=1000) → ─────────────────── → SELECT balance (=800) ← 변경됨!
TX-B:  ─────────────────── → UPDATE balance=800 → COMMIT
```

같은 TX 안에서 같은 SELECT를 두 번 실행해도 **다른 결과가 나올 수 있음** (Non-Repeatable Read).

#### REPEATABLE READ — MySQL vs PostgreSQL

**MySQL (InnoDB):**
- 스냅샷은 TX 내 **첫 번째 consistent read(일반 SELECT)** 시점에 생성
- `SELECT ... FOR UPDATE`(locking read)는 스냅샷을 생성하지 않고, 최신 커밋된 행을 읽음
- 따라서 locking read 후 일반 SELECT을 하면 그 시점에 새 스냅샷이 생성됨

```
MySQL REPEATABLE READ:

TX-A:  BEGIN → FOR UPDATE (wallet) → ──────── → SELECT (idem_key) ← 이 시점에 스냅샷 생성
TX-B:  ──── → UPDATE idem_key → COMMIT ─────
                                         ↑
                              TX-A의 SELECT가 TX-B 커밋 결과를 볼 수 있음
                              (스냅샷이 SELECT 시점에 생성되므로)
```

**PostgreSQL:**
- 스냅샷은 TX 내 **첫 번째 쿼리 (종류 무관)** 시점에 고정
- `FOR UPDATE`도 스냅샷 생성 시점에 영향을 줌
- 이후 일반 SELECT은 이미 고정된 스냅샷을 사용

```
PostgreSQL REPEATABLE READ:

TX-A:  BEGIN → FOR UPDATE (wallet) ← 이 시점에 스냅샷 고정 → SELECT (idem_key)
TX-B:  ──── → UPDATE idem_key → COMMIT ─────
                                         ↑
                              TX-A의 SELECT가 TX-B 커밋 결과를 못 볼 수 있음!
                              (스냅샷이 FOR UPDATE 시점에 이미 고정되었으므로)
```

### locking read vs consistent read

| 구분 | SQL 예시 | 스냅샷 사용 | 락 획득 |
|------|---------|-----------|--------|
| **Consistent Read** (일반 SELECT) | `SELECT * FROM wallet WHERE id = 1` | 스냅샷 읽음 (MVCC) | 락 없음 |
| **Locking Read** | `SELECT * FROM wallet WHERE id = 1 FOR UPDATE` | 스냅샷 무시, 최신 커밋된 행을 읽음 | 배타 락 획득 |

**핵심:** `FOR UPDATE`는 REPEATABLE READ에서도 **스냅샷을 무시하고 최신 커밋된 데이터를 읽는다**. 이것이 비관적 락이 동시성 제어에서 안전한 이유.

### 격리 수준별 실무 사용 기준

| 격리 수준 | 적합한 사용처 | 주의사항 |
|-----------|-------------|---------|
| **READ COMMITTED** | 대부분의 OLTP 서비스 (웹 API, 실시간 처리) | Non-Repeatable Read 가능 → 필요하면 `FOR UPDATE`로 보완 |
| **REPEATABLE READ** | 배치 처리, 리포팅 (TX 내 일관된 스냅샷 필요) | 동시 UPDATE 충돌 시 serialization failure 발생 가능 (PostgreSQL) |
| **SERIALIZABLE** | 금융 정산, 감사 로그 등 절대 정합성 | 성능 크게 저하, 데드락 빈도 증가 |

### PostgreSQL REPEATABLE READ의 serialization failure

PostgreSQL에서 REPEATABLE READ 사용 시, 두 TX가 같은 행을 동시에 UPDATE하면:

```
TX-A:  BEGIN → SELECT (스냅샷 고정) → UPDATE wallet SET balance=800 → COMMIT
TX-B:  BEGIN → SELECT (스냅샷 고정) → UPDATE wallet SET balance=700 → ERROR!
                                                                      ↑
                                          "could not serialize access due to concurrent update"
```

MySQL은 이 경우 TX-B가 **대기 후 TX-A 커밋 결과에 적용**하지만, PostgreSQL은 **즉시 에러**를 반환. 애플리케이션에서 재시도 로직이 필요.
