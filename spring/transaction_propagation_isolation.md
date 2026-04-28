# Spring 트랜잭션 전파와 격리 실전 패턴

> **관련 문서**
> - [Spring Transaction](./transaction.md)
> - [JPA 기본](./JPA.md)
> - [DB 트랜잭션](../db/db_transaction.md)

---

## 1. 이 문서의 범위

`transaction.md`가 전파 속성의 기본 개념을 정리한다면, 이 문서는 실무에서 자주 터지는 트랜잭션 문제를 다룬다.

- `REQUIRED`와 `REQUIRES_NEW` 선택 기준
- rollback-only와 `UnexpectedRollbackException`
- self-invocation 문제
- 격리 수준과 DB 락의 경계
- 이벤트/감사 로그/실패 이력 저장 패턴

---

## 2. REQUIRED 기본값의 의미

`REQUIRED`는 기존 트랜잭션이 있으면 참여하고, 없으면 새로 만든다.

```java
@Transactional
public void placeOrder() {
    orderService.create();
    paymentService.pay();
}
```

`create()`와 `pay()`가 모두 `REQUIRED`라면 하나의 트랜잭션에 묶인다.
중간에 RuntimeException이 발생하면 전체가 롤백된다.

장점:
- 원자성 보장
- 이해하기 쉬움

주의:
- 내부 작업 중 하나만 독립 커밋하고 싶다면 적합하지 않다.
- 외부 API 호출을 긴 트랜잭션 안에서 수행하면 커넥션 점유 시간이 길어진다.

---

## 3. REQUIRES_NEW 사용 기준

`REQUIRES_NEW`는 기존 트랜잭션을 잠시 보류하고 새 트랜잭션을 시작한다.

대표 사용처:
- 실패 이력 저장
- 감사 로그 저장
- 알림 발송 요청 기록
- outbox 이벤트를 독립적으로 남겨야 하는 경우

```java
@Service
public class AuditLogService {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(AuditLog log) {
        auditLogRepository.save(log);
    }
}
```

부모 트랜잭션이 롤백돼도 감사 로그는 커밋된다.

주의:
- 새 DB 커넥션을 추가로 사용한다.
- 반복 호출되면 커넥션 풀 고갈 위험이 있다.
- 같은 클래스 내부 호출이면 프록시를 거치지 않아 동작하지 않는다.

---

## 4. rollback-only와 UnexpectedRollbackException

다음 코드는 예외를 catch했지만 커밋 시점에 실패할 수 있다.

```java
@Transactional
public void parent() {
    try {
        childService.doWork(); // REQUIRED
    } catch (RuntimeException e) {
        log.warn("ignore", e);
    }

    repository.save(new Something());
}
```

`childService.doWork()`에서 RuntimeException이 발생하면 현재 트랜잭션이 rollback-only로 표시된다.
부모가 예외를 catch해도 rollback-only는 해제되지 않는다.

결과:

```
commit 시도 -> UnexpectedRollbackException
```

해결:
- 실패해도 계속 진행해야 하는 작업은 `REQUIRES_NEW`로 분리한다.
- 예외를 트랜잭션 안에서 삼키지 않는다.
- 비즈니스 실패를 예외가 아닌 상태 값으로 모델링할 수 있는지 검토한다.

---

## 5. self-invocation 문제

Spring `@Transactional`은 프록시 기반 AOP다.
같은 클래스 내부에서 메서드를 호출하면 프록시를 거치지 않는다.

```java
@Service
public class OrderService {
    public void place() {
        saveHistory(); // this.saveHistory() 직접 호출
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveHistory() {
        historyRepository.save(...);
    }
}
```

`saveHistory()`의 `REQUIRES_NEW`는 적용되지 않는다.

권장:

```java
@Service
public class OrderHistoryService {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveHistory(...) {
        historyRepository.save(...);
    }
}
```

트랜잭션 경계가 다르면 클래스를 분리하는 편이 의도가 명확하다.

---

## 6. 격리 수준 선택

Spring의 격리 수준은 DB 트랜잭션 격리 수준을 설정한다.

```java
@Transactional(isolation = Isolation.READ_COMMITTED)
public void transfer(...) { ... }
```

| 격리 수준 | 특징 |
|---|---|
| `READ_COMMITTED` | 커밋된 데이터만 읽음, 많은 운영 DB 기본값 |
| `REPEATABLE_READ` | 같은 트랜잭션 내 반복 읽기 일관성, MySQL InnoDB 기본값 |
| `SERIALIZABLE` | 가장 강하지만 동시성 저하 |

격리 수준만으로 모든 동시성 문제가 해결되지는 않는다.
잔액 차감, 재고 감소처럼 "읽고 계산하고 쓰는" 로직은 Lost Update가 발생할 수 있다.

대응:
- 비관적 락: `SELECT ... FOR UPDATE`
- 낙관적 락: `@Version`
- 원자적 UPDATE: `UPDATE wallet SET balance = balance - ? WHERE id = ? AND balance >= ?`
- 메시지 큐로 key 단위 직렬화

---

## 7. 트랜잭션 안에서 외부 API 호출

```java
@Transactional
public void pay(Order order) {
    order.markPaying();
    pgClient.approve(order); // 외부 API
    order.markPaid();
}
```

문제:
- 외부 API 지연 동안 DB 커넥션과 락을 오래 점유한다.
- API 성공 후 DB 커밋 실패 시 외부 시스템과 불일치가 생긴다.
- 재시도 시 중복 승인 위험이 있다.

대안:
- 상태를 `PAYING`으로 짧게 커밋한 뒤 외부 API 호출
- PG 요청에는 idempotency key를 사용
- 결과는 webhook 또는 outbox/SAGA로 최종 반영
- 불일치는 reconciliation 배치로 감지

---

## 8. 실무 체크리스트

- 트랜잭션 안에서 외부 API를 호출하는지 확인한다.
- 실패 이력/감사 로그는 부모 롤백과 독립이어야 하는지 판단한다.
- `REQUIRES_NEW` 사용 시 커넥션 풀 여유를 확인한다.
- 내부 호출로 `@Transactional`이 무력화되지 않는지 확인한다.
- catch 후 계속 진행하는 코드에서 rollback-only 가능성을 점검한다.
- 격리 수준 변경 전에 DB 락/쿼리 패턴으로 해결할 수 있는지 본다.
- 동시성 정합성은 테스트로 재현한다.

### 면접 포인트

- Q: `REQUIRED` 내부 메서드 예외를 catch했는데도 `UnexpectedRollbackException`이 나는 이유는?
- Q: `REQUIRES_NEW`를 쓰면 왜 커넥션 풀이 더 필요해지는가?
- Q: 같은 클래스 내부 호출에서 `@Transactional`이 동작하지 않는 이유는?
- Q: 트랜잭션 안에서 외부 API를 호출하면 어떤 문제가 생기는가?
