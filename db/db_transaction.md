# DB Transaction

## 트랜잭션 격리 수준 (Transaction Isolation Level)

1. Read Uncommitted (커밋되지 않은 데이터 읽기)
   - 트랜잭션의 내용이 커밋이나 롤백과 상관없이 다른 트랜잭션에 조회 가능
   - Dirty Read, Non-Repeatable Read, Phantom Read 발생 가능
2. Read Committed (커밋된 데이터 읽기)
   - 트랜잭션이 커밋된 데이터만 조회 가능
   - Non-Repeatable Read, Phantom Read 발생 가능
   - Oracle, SQL-SERVER 의 default isolation level
3. Repeatable Read (반복가능한 읽기)
   - 트랜잭션이 시작되기 전에 커밋된 내용에 대해서만 조회 가능
   - Phantom Read 발생 가능
   - MySQL의 default isolation level
4. Serializable (직렬화)
   - 트랜잭션의 내용이 완전히 격리되어 다른 트랜잭션에 조회 불가
   - 가장 단순하면서 엄격한 격리수준이지만 성능측면에서 처리성능이 가장 낮음
   - Dirty Read, Non-Repeatable Read, Phantom Read 발생 불가

## 관련 용어

- Dirty Read: 다른 트랜잭션이 커밋하지 않은 데이터를 읽는 현상
- Non-Repeatable Read: 같은 쿼리를 두 번 실행했을 때, 다른 결과를 얻는 현상
    - 한 트랜잭션내에서 반복읽기를 수행하면 다른 트랜잭션의 커밋여부에 따라 조회결과가 달라지는 문제
    - NON-REPEATABLE READ는 하나의 레코드에 초점된 문제로, 트랜잭션A에서 레코드의 값을 UPDATE했을 때, 트랜잭션 B가 UPDATE된 값을 읽어와 데이터 정합성이 깨지는 것을 의미
- Phantom Read: 같은 쿼리를 두 번 실행했을 때, 다른 row를 얻는 현상
    - PHANTOM READ는 레코드 집합에 초점된 문제로, 트랜잭션 A가 레코드를 INSERT하거나 DELETE했을 때, 트랜잭션 B가 해당 레코드가 포함된 집합을 읽어올 때 데이터 정합성이 깨지는 것을 의미

---

## ACID 속성

ACID는 원자성(Atomicity), 일관성(Consistency), 격리성(Isolation), 지속성(Durability)의 약자이며, 데이터베이스 트랜잭션이 안전하게 수행된다는 것을 보장하기 위한 성질을 의미합니다.

### 원자성 (Atomicity)
트랜잭션 내부 연산들이 부분적으로 실행되고 중단되지 않는 것을 보장합니다. 트랜잭션은 전체 성공과 전체 실패 중 한 가지만 수행합니다.

예시: 계좌 이체 트랜잭션에서 A 계좌 출금 후 B 계좌 입금 과정에서 에러가 발생하면 출금 과정도 함께 취소되어야 합니다.

### 일관성 (Consistency)
트랜잭션이 성공적으로 완료되면 일관성 있는 데이터베이스 상태로 유지되는 것을 보장합니다. 제약조건과 같이 데이터베이스에 정의된 규칙을 트랜잭션이 위반하는 경우에는 해당 트랜잭션은 취소되어야 합니다.

### 격리성 (Isolation)
동시에 실행되는 여러 트랜잭션이 서로 독립적임을 보장합니다. 트랜잭션 밖에서 어떠한 연산도 중간 단계의 데이터를 볼 수 없음을 의미합니다. 가장 엄격할 경우에는 트랜잭션을 순차적으로 실행합니다.

### 지속성 (Durability)
성공적으로 수행된 트랜잭션은 영원히 반영되어야 함을 보장합니다. 시스템에 장애가 발생해도 성공적으로 수행된 트랜잭션의 결과는 항상 데이터베이스에 반영되어 있어야 합니다. 전형적으로 트랜잭션은 로그로 남고, 로그가 저장되어야 트랜잭션이 성공되었다고 간주하며, 추후 장애 발생 시 이 로그를 활용해 데이터베이스를 회복합니다.

---

## 낙관적 락 vs 비관적 락

낙관적 락과 비관적 락은 데이터베이스 트랜잭션에서 동시성 제어를 위한 주요 기법입니다.

### 낙관적 락 (Optimistic Lock)
데이터 충돌이 적을 것으로 가정하고, 데이터를 읽을 때 락을 설정하지 않고 트랜잭션이 데이터를 수정할 때 충돌이 발생하지 않았는지 확인하는 방식입니다.
- `version`과 같은 별도의 구분 컬럼을 사용해서 데이터가 변경되었는지 확인
- 충돌이 발생하면 데이터베이스가 아닌 애플리케이션에서 직접 롤백하거나 재시도 처리
- 조회 작업이 많고 동시 접근 성능이 중요한 경우 유리

### 비관적 락 (Pessimistic Lock)
데이터 충돌이 많을 것으로 가정하고, 트랜잭션이 시작될 때 공유락(S-Lock) 또는 배타락(X-Lock)을 설정하여 다른 트랜잭션이 해당 데이터에 접근하지 못하도록 하는 방식입니다.
- S-Lock: 다른 트랜잭션에서 읽기는 가능하지만 쓰기는 불가능
- X-Lock: 다른 트랜잭션에서 읽기, 쓰기 모두 불가능
  - MySQL은 일관된 읽기(Consistent Nonlocking Reads)를 지원하여 X-Lock이 걸려있어도 단순 SELECT로 읽을 수 있음
- 데이터 충돌이 자주 발생하거나 데이터 무결성이 중요한 경우 유리

### 비교

| 항목 | 낙관적 락 | 비관적 락 |
|---|---|---|
| 충돌 가정 | 충돌이 적다고 가정 | 충돌이 많다고 가정 |
| DB 락 사용 여부 | 미사용 | 사용 |
| 성능 | 충돌 없을 때 더 좋음 | 대기 발생으로 저하 가능 |
| 충돌 처리 | 애플리케이션에서 롤백/재시도 | 애초에 충돌 방지 |

---

## 최종적 일관성 (Eventual Consistency)

**최종적 일관성(Eventual Consistency)** 이란 분산 시스템에서 고가용성을 유지하기 위해서 사용하는 일관성 모델입니다.

- 데이터가 수정되면 변경 내용은 비동기적으로 다른 노드에 전파되어 일시적으로 각 노드의 데이터가 다를 수 있음
- 시간이 지나면 모든 노드에 변경 사항이 전달되어 결국 모든 노드가 동일한 데이터를 가지게 됨
- 복제를 수행 중인 노드에 대해 조회 연산을 허용하여 높은 가용성을 유지
- 단점: 일시적인 데이터 불일치로 클라이언트가 오래된 데이터를 읽을 수 있음

### 강한 일관성 (Strong Consistency) 비교

| 항목 | 최종적 일관성 | 강한 일관성 |
|---|---|---|
| 데이터 동기화 시점 | 비동기, 시간 차 있음 | 즉시, 연산 직후 |
| 가용성 | 높음 | 복제 완료 전까지 읽기 차단 |
| 일시적 불일치 | 발생 가능 | 발생하지 않음 |
| 사용 사례 | AP 시스템, NoSQL | CP 시스템, 금융 트랜잭션 |

CAP 이론에서 AP 시스템은 최종적 일관성을 채택하고, 파티션 해결 후 동기화 작업을 통해 최종적 일관성을 보장합니다.

---

## 참고 링크

- [트랜잭션 격리 수준 (Isolation Level)](https://mangkyu.tistory.com/299)
- [ACID 데이터베이스와 BASE 데이터베이스의 차이점](https://aws.amazon.com/ko/compare/the-difference-between-acid-and-base-database/)
- [분산 데이터베이스 탐구: 데이터 복제와 일관성](https://loosie.tistory.com/886)
- [Optimistic vs. Pessimistic locking (Stack Overflow)](https://stackoverflow.com/questions/129329/optimistic-vs-pessimistic-locking)
- [Baeldung - Optimistic Locking in JPA](https://www.baeldung.com/jpa-optimistic-locking)
- [Baeldung - Pessimistic Locking in JPA](https://www.baeldung.com/jpa-pessimistic-locking)
- [System Design Interview Concepts – Eventual Consistency](https://acodersjourney.com/eventual-consistency/)
- [Google Cloud - Eventual Consistency](https://cloud.google.com/datastore/docs/articles/balancing-strong-and-eventual-consistency-with-google-cloud-datastore?hl=ko)
