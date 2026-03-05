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

## 트랜잭션 격리수준 (Transaction Isolation Level)

| 격리수준 | Dirty Read | Non-Repeatable Read | Phantom Read |
|---|---|---|---|
| READ UNCOMMITTED | O | O | O |
| READ COMMITTED | X | O | O |
| REPEATABLE READ | X | X | O (InnoDB는 갭락으로 방지) |
| SERIALIZABLE | X | X | X |

- **READ COMMITTED**: Oracle 기본값
- **REPEATABLE READ**: MySQL 기본값
- **Dirty Read**: 커밋되지 않은 데이터를 읽어 롤백 시 불일치 발생
- **Non-Repeatable Read**: 같은 쿼리를 두 번 실행했을 때 다른 결과 (UPDATE/DELETE)
- **Phantom Read**: 같은 쿼리를 두 번 실행했을 때 없던 행이 나타남 (INSERT)

데이터 정합성과 성능은 반비례: 낮은 격리수준 = 높은 동시처리 / 높은 격리수준 = 높은 정합성
