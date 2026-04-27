# JPA

## 복합키 처리

### IdClass
- 개별 필드에 직접 접근할 수 있다는 이점
- 직관적인 구현
- JPQL, QueryDSL 사용시에 복잡도가 낮아짐
  ```java
  @Entity
  @IdClass(StudentId.class)
  public class Student {
      @Id
      private Long id;
      @Id
      private Long classId;
  }

  public class StudentId implements Serializable {
      private Long id;
      private Long classId;
      // equals(), hashCode()
  }
  ```

### EmbeddedId
- 단일 객체로 복합 키를 쉽게 캡슐화할 수 있다는 이점
  ```java
  @Entity
  public class Student {
      @EmbeddedId
      private StudentId id;
  }

  @Embeddable
  public class StudentId implements Serializable {
      private Long id;
      private Long classId;
      // equals(), hashCode()
  }
  ```

---

## JPA 사용 이유

JPA(Spring Data JPA)는 데이터 액세스 계층 구현의 복잡성을 크게 줄여주는 것이 핵심 목적이다.

- **DAO 구현 불필요**: `JpaRepository` 등 인터페이스를 상속하는 것만으로 표준 CRUD 메서드를 즉시 사용할 수 있다.
- **자동 쿼리 생성**: 메서드 이름 규칙(예: `findByNameAndEmail`)만으로 JPA가 쿼리를 자동 생성한다.
- **커스텀 쿼리 지원**: `@Query`를 통해 JPQL 직접 작성, 또는 Querydsl/Specification 활용 가능.
- **트랜잭션 관리 간소화**: 클래스 수준에서 읽기 전용 `@Transactional`이 적용되고, 필요한 메서드만 재정의.
- **페이지네이션, Auditing** 등 자주 쓰이는 기능을 기본 제공.

---

## JPA, Hibernate, Spring Data JPA 차이

| 구분 | 성격 | 설명 |
|---|---|---|
| **JPA** | 명세(Specification) | 자바에서 ORM을 사용하는 방식을 정의한 인터페이스/규약. 구현체를 제공하지 않는다. |
| **Hibernate** | JPA 구현체 | `EntityManager` 등 JPA 인터페이스를 실제로 구현한 라이브러리. DataNucleus, EclipseLink 등으로 교체 가능. |
| **Spring Data JPA** | 추상화 모듈 | JPA 위에 `Repository` 인터페이스를 추가로 추상화하여, 규칙에 맞는 메서드 이름만으로 구현체를 자동 생성·Bean 등록해 준다. |

**계층 관계**: `Spring Data JPA` → `JPA(인터페이스)` → `Hibernate(구현체)` → `DB`

- `SimpleJpaRepository`(Spring Data JPA 기본 구현체) 내부에서 `EntityManager`(JPA)를 직접 사용한다.

---

## Spring Data JPA에서 새로운 Entity 판단

`SimpleJpaRepository.save()`는 `isNew()` 결과에 따라 `persist` 또는 `merge`를 선택한다.

**판단 우선순위**

1. 엔티티가 `Persistable<T>` 구현 → `entity.isNew()` 직접 호출
2. `@Version` 필드가 wrapper 타입 → `null` 여부로 판단
3. 그 외 기본 → `@Id` 필드 기준
   - wrapper 타입: `null`이면 신규
   - primitive number 타입: `0` 또는 `0L`이면 신규
   - wrapper `Long`, `Integer` 등은 `null`이어야 신규

**직접 ID를 할당하는 경우의 문제점**

`@GeneratedValue` 없이 ID를 직접 지정하면, persist 전에도 ID가 존재하므로 `isNew()`가 `false`를 반환한다.
이때 `save()`는 `merge()`를 호출하고, merge는 DB 조회를 먼저 수행하므로 **불필요한 SELECT 쿼리가 발생**한다.

**해결책**: `Persistable<T>`를 구현하고 `@CreatedDate` 등과 조합해 `isNew()`를 직접 제어한다.

```java
@Override
public boolean isNew() {
    return createdAt == null;
}
```

참고: [save()시 식별자가 존재하는 경우 어떻게 동작할까?](https://ttl-blog.tistory.com/807)

---

## JPA ddl-auto 옵션

`spring.jpa.hibernate.ddl-auto` 설정으로 애플리케이션 시작 시 스키마 처리 방식을 제어한다.

| 옵션 | 동작 | 권장 환경 |
|---|---|---|
| `none` | 아무 작업 안 함. 스키마를 수동 관리. | 프로덕션 |
| `validate` | 엔티티와 DB 스키마 일치 여부만 검증. 변경 없음. | 프로덕션 |
| `update` | 변경된 엔티티 필드를 스키마에 반영. 기존 데이터 유지. | 개발 (프로덕션 주의) |
| `create` | 시작 시 기존 스키마 삭제 후 재생성. 데이터 삭제. | 개발 초기 |
| `create-drop` | `create`와 동일 + 애플리케이션 종료 시 스키마 삭제. | 테스트 |

**프로덕션 스키마 변경 권장 방식**: Flyway 또는 Liquibase 같은 마이그레이션 도구 사용

---

## 엔티티 매니저

**영속성 컨텍스트**: 엔티티를 영구 저장하는 논리적 공간. 1차 캐싱, 쓰기 지연(Write-behind), 변경 감지(Dirty Checking) 기능을 제공한다.

**엔티티 매니저**: 영속성 컨텍스트와 상호작용하며 엔티티 상태를 관리하는 인터페이스.

**엔티티의 4가지 상태**

| 상태 | 설명 | 전환 방법 |
|---|---|---|
| **비영속** | 새로 생성된 객체, 컨텍스트와 무관 | `new Member()` |
| **영속** | 컨텍스트에서 관리 중. 변경 사항이 DB에 자동 반영됨 | `em.persist()`, `em.merge()`, `em.find()` |
| **준영속** | 한 번 영속됐다가 컨텍스트에서 분리된 상태 | `em.detach()`, `em.clear()`, `em.close()`, 트랜잭션 종료 |
| **삭제** | 컨텍스트 및 DB에서 제거 예정 | `em.remove()` |

**영속성 컨텍스트 주요 기능**
- **1차 캐시**: 동일 트랜잭션 내에서 반복 조회 시 DB 접근 없이 캐시에서 조회
- **동일성 보장**: 같은 엔티티를 반복 조회해도 동일한 객체 참조 반환
- **변경 감지(Dirty Checking)**: 트랜잭션 커밋 시점에 변경된 엔티티를 DB에 자동 반영
- **쓰기 지연**: flush() 시점까지 INSERT/UPDATE 쿼리를 모아서 실행

---

## JPA N+1 문제

**정의**: 연관 관계가 있는 엔티티를 조회할 때, 조회된 결과 N개에 대해 연관 엔티티를 가져오는 쿼리가 N번 추가 실행되는 현상. (1번 조회 + N번 추가 = N+1)

| 패치 전략 | 동작 | N+1 발생 여부 |
|---|---|---|
| **즉시 로딩(EAGER)** | JPQL은 fetch join 없는 SQL만 실행 → N개 결과에 대해 Hibernate가 EAGER 전략을 맞추기 위해 N번 추가 SELECT | 발생 |
| **지연 로딩(LAZY)** | 연관 엔티티를 프록시로 초기화 | 프록시 실제 사용 시 N+1 발생 |

**해결 방법**

1. **Fetch Join**: JPQL에서 `join fetch` 구문으로 연관 엔티티를 한 번에 로딩

```sql
select distinct u from User u left join fetch u.posts
```

2. **@EntityGraph**: 쿼리 메서드에 어노테이션으로 선언적 fetch join 적용

```java
@EntityGraph(attributePaths = {"posts"}, type = EntityGraphType.FETCH)
List<User> findAll();
```

참고: [JPA 모든 N+1 발생 케이스과 해결책](https://velog.io/@jinyoungchoi95/JPA-%EB%AA%A8%EB%93%A0-N1-%EB%B0%9C%EC%83%9D-%EC%BC%80%EC%9D%B4%EC%8A%A4%EA%B3%BC-%ED%95%B4%EA%B2%B0%EC%B1%85)

---

## JPA ID 생성 전략

**직접 할당**: `@Id`만 사용. 개발자가 직접 ID 값을 세팅해야 한다.

**자동 할당**: `@Id` + `@GeneratedValue(strategy = GenerationType.XXX)` 사용.

| 전략 | 설명 | 주요 DB | 특이사항 |
|---|---|---|---|
| **IDENTITY** | ID 생성을 DB에 위임 (AUTO_INCREMENT) | MySQL, PostgreSQL | 쓰기 지연 불가. persist 시 즉시 INSERT. 배치 INSERT 불가. |
| **SEQUENCE** | DB 시퀀스 객체를 통해 ID 채번 | Oracle, PostgreSQL 등 | persist 시 시퀀스 조회 후 엔티티에 ID 할당, 실제 INSERT는 flush 시점. `allocationSize`로 시퀀스 값을 미리 확보 가능 |
| **TABLE** | 키 전용 테이블로 시퀀스 흉내 | 모든 DB | SELECT+UPDATE 2회 통신으로 SEQUENCE보다 성능 낮음 |
| **AUTO** | 방언(Dialect)에 따라 위 세 전략 중 자동 선택 | 모든 DB | DB 변경 시 코드 수정 불필요 |

**IDENTITY 전략 핵심 주의사항**: 영속성 컨텍스트에 저장하려면 식별자가 필요한데, IDENTITY는 INSERT 후에야 ID가 생긴다. 따라서 `em.persist()` 호출 즉시 INSERT가 실행되어 **쓰기 지연이 동작하지 않는다**.

**SEQUENCE 전략 핵심 주의사항**: `@SequenceGenerator`의 `allocationSize` 기본값은 50이다.
JPA는 시퀀스 값을 매번 하나씩 조회하지 않고 일정 범위를 미리 확보해서 ID를 할당할 수 있다.
따라서 SEQUENCE 전략은 IDENTITY와 달리 쓰기 지연과 배치 INSERT에 유리하다.
DB 시퀀스의 `INCREMENT BY`와 JPA `allocationSize`가 맞지 않으면 ID 충돌이나 낭비가 생길 수 있으므로 함께 관리해야 한다.

참고: [Identity 전략으로는 Batch Insert가 불가능한 이유](https://dkswnkk.tistory.com/682)

---

## Record를 DTO로 사용하는 이유

**Record란**: Java 16 정식 출시. 모든 필드가 `final`인 **불변(Immutable) 클래스**. 생성자, getter, `equals()`, `hashCode()`, `toString()`을 자동 생성한다.

```java
// 기존 방식
public class MemberDto { /* 생성자, getter 등 직접 작성 */ }

// Record 방식 (한 줄로 동일한 효과)
public record MemberDto(String name, String email, int age) {}
```

**DTO로 활용하는 이유**
- **보일러 플레이트 코드 제거**: 기존 클래스 DTO 대비 코드량이 대폭 줄어든다.
- **불변성 보장**: 멀티 스레드 환경에서도 안전하게 데이터 전달 가능.
- **명확한 의도**: 데이터 전달용 객체임을 코드 구조로 표현.

**Record의 한계**: 상속(`extends`) 불가, 비즈니스 로직 포함 부적합, Java 16 미만 호환 불가

---

## @OneToOne Lazy Loading 주의점

**핵심**: 양방향 `@OneToOne`에서 **연관관계의 주인이 아닌 쪽(mappedBy 쪽)** 을 조회하면 `FetchType.LAZY`를 설정해도 **Lazy Loading이 동작하지 않는다**.

**원인**
- DB 테이블 구조상 연관관계의 주인이 아닌 쪽은 FK를 갖지 않는다.
- JPA는 연관 엔티티가 존재하는지 알 수 없어서, null로 초기화할지 프록시로 초기화할지 결정하지 못한다.
- 결국 존재 여부 확인을 위한 **추가 SELECT 쿼리를 항상 실행**하게 되어 Lazy Loading이 무의미해진다.

```java
@Entity
public class User {
    @OneToOne(mappedBy = "user", fetch = FetchType.LAZY) // LAZY 설정해도 무효
    private Account account;
}
```

**해결 방향**: 단방향으로 모델링하거나, 해당 지연 로딩이 정말 필요한지 설계를 재검토한다.

---

## JPA Fetch Join과 페이징 주의점

**핵심 문제**: `@OneToMany`(컬렉션) 관계에서 Fetch Join과 페이징을 **동시에 사용하면 OOM(OutOfMemoryError)이 발생**할 수 있다.

**발생 이유**
- `OneToMany` Fetch Join 시 조인 결과는 카티션 프로덕트로 부모 엔티티 행이 증가한다.
- DB 레벨에서 `LIMIT/OFFSET`을 적용하면 의도한 페이지가 나오지 않으므로, Hibernate는 **전체 데이터를 메모리에 올린 뒤 애플리케이션 레벨에서 페이징**한다.
- 경고 메시지: `firstResult/maxResults specified with collection fetch; applying in memory`

**해결 방법**: Fetch Join 제거 + `@BatchSize` 적용으로 N+1을 방지하면서 페이징 가능.

```java
@Entity
class Product {
    @BatchSize(size = 10)
    @OneToMany(mappedBy = "product")
    private List<ProductCategory> categories;
}
```

동작 원리: 부모 엔티티 ID들을 IN 절에 묶어 자식 엔티티를 배치 조회한다.

| 상황 | 권장 방식 |
|---|---|
| `@ToOne` + 페이징 | Fetch Join 사용 가능 (행 증가 없음) |
| `@ToMany` + 페이징 | Fetch Join 제거 + `@BatchSize` 또는 `default_batch_fetch_size` 설정 |

---

## OSIV(Open Session In View)

- OSIV(Open Session In View)는 영속성 컨텍스트를 뷰까지 열어둔다는 의미
- 뷰에서도 지연로딩이 가능함
- 트랜잭션 범위 밖에서 지연로딩을 반드시 수행해야 하는 경우 비활성화하기 어려울 수도 있음
- `spring.jpa.open-in-view=false` 로 비활성화하면 커넥션을 더 일찍 반환하여 리소스 효율 향상

---

## 영속성 컨텍스트 심층 동작 (시니어 심화)

### 1차 캐시 세부 동작

```
EntityManager.find() 호출 시:
1. 1차 캐시(Map<EntityKey, Entity>) 조회
2. 있으면 → DB SELECT 없이 캐시 반환 (같은 트랜잭션 내)
3. 없으면 → DB SELECT 실행 후 1차 캐시에 등록 + 스냅샷 저장
```

**중요**: 같은 트랜잭션 내에서 동일 PK로 두 번 조회해도 SELECT는 1회.
단, `@Query("SELECT w FROM Wallet w WHERE w.id = :id")` 형태의 **JPQL**은 1차 캐시 조회 전 **flush() 트리거** → DB flush 후 캐시 조회.

### JPQL 실행 시 자동 flush

Hibernate에서 JPQL이 실행되면 FlushModeType.AUTO 기준으로 **쿼리 결과에 영향을 줄 수 있는 변경이 있을 때** flush가 발생한다:
- 이유: JPQL은 DB에서 쿼리를 실행하므로 관련 테이블의 미반영 변경이 있으면 결과 불일치 발생
- Hibernate는 Query Space를 분석해 변경된 테이블과 쿼리 대상 테이블이 겹칠 때 flush할 수 있다.
- 따라서 관련 없는 테이블의 변경은 JPQL 실행 전 flush되지 않을 수 있다.
- FlushModeType.AUTO(기본값): 필요한 경우 JPQL 실행 전 자동 flush
- FlushModeType.COMMIT: 커밋 시에만 flush (JPQL 결과 불일치 주의)

### flush() 세 가지 타이밍

1. **트랜잭션 커밋 직전** (가장 흔한 경우)
2. **JPQL/Criteria 쿼리 실행 직전** (FlushModeType.AUTO)
3. **명시적 em.flush() 호출**

flush()는 영속성 컨텍스트의 변경을 DB에 SQL로 반영하는 것이다.
하지만 트랜잭션은 아직 끝나지 않았으므로 이후 예외가 발생하면 flush된 변경도 함께 롤백된다.
즉, **flush ≠ commit** 이며 flush는 DB 반영 시점, commit은 트랜잭션 확정 시점이다.

### Dirty Checking 내부 동작

```
1. 엔티티 조회 시 → 스냅샷(초기 상태) 저장
2. flush() 시 → 현재 상태 vs 스냅샷 비교
3. 차이 있으면 → UPDATE 쿼리 자동 생성
4. 기본: 모든 컬럼 UPDATE (@DynamicUpdate로 변경 컬럼만 가능)
```

**주의**: @Transactional 없이 변경하면 flush() 없음 → DB 반영 안 됨.

### SELECT FOR UPDATE 이후 save() 흐름

> `isNew()` 판단 기준 및 persist/merge 기본 동작은 "Spring Data JPA에서 새로운 Entity 판단" 섹션 참조.

```java
// 비관적 락 패턴 예시
Wallet wallet = walletJpaRepo.findByIdForUpdate(walletId);   // 1. 비관적 락 SELECT
wallet.withdraw(amount);                                       // 2. 도메인 변경
walletJpaRepo.save(wallet);                                    // 3. save() 호출
```

3번에서 `wallet`은 이미 1차 캐시에 있는 영속 상태이므로:
- `save()` → `merge()` 경로 진입 (ID 있으므로)
- `merge()`는 내부적으로 1차 캐시 확인 후 이미 관리 중이면 그대로 반환
- 실제로 **SELECT 추가 발생하지 않음** — 1차 캐시 히트

그러나 `findById()` 추가 호출 시:
- 같은 트랜잭션 내 → 1차 캐시 반환 (SELECT 없음)
- 다른 트랜잭션이면 → 새 SELECT 발생

### 면접 포인트 (시니어 검증용)

- Q: JPQL을 실행하면 왜 flush()가 발생하는가?
- Q: SimpleJpaRepository.save()가 이미 영속 상태인 엔티티에서 SELECT를 발생시키지 않는 이유는?
- Q: 같은 트랜잭션 내에서 findByIdForUpdate() 이후 findById()를 호출하면 SELECT가 나가는가?
- Q: @DynamicUpdate 없이 단일 컬럼만 변경해도 모든 컬럼이 UPDATE 되는 이유는?
