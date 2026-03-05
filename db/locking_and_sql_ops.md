# DB Locking And SQL Operations

## DB Lock

### 공유 락 (Shared Lock)
읽기 락(Read Lock)이라고 부르며, 공유 락이 걸린 데이터에 대해서 다른 트랜잭션에서도 공유 락을 획득할 수 있지만, 배타 락은 획득할 수 없습니다. 즉, 공유 락을 사용하면 트랜잭션 내에서 조회한 데이터가 변경되지 않는다는 것을 보장합니다.

```sql
SELECT * FROM table_name WHERE id = 1 FOR SHARE;
```

### 배타 락 (Exclusive Lock)
쓰기 락(Write Lock)이라고 부르며, 배타 락이 걸린 데이터에 대해서 다른 트랜잭션에서는 공유 락과 배타 락을 획득할 수 없습니다. 즉, 배타 락을 획득한 트랜잭션은 데이터에 대한 독점권을 가집니다.

```sql
SELECT * FROM table_name WHERE id = 1 FOR UPDATE;
```

### 정리
- 공유 락이 걸린 데이터는 다른 트랜잭션에서 공유 락을 획득 가능
- 배타 락이 걸린 데이터는 다른 트랜잭션에서 어떤 종류의 락도 획득할 수 없어 대기 상태 발생
- 락과 격리 수준의 차이
  - 격리 수준: 다른 트랜잭션 변경 내용을 어떻게 볼지 결정
  - 락: 특정 데이터 접근 자체를 제어

---

## 데드락 (Dead Lock)

**데드 락(Dead Lock)** 이란 교착 상태로, 두 개 이상의 트랜잭션이 서로 필요로 하는 데이터의 락을 점유하고 있어서 무한히 대기하는 상황을 말합니다.

예시:
- 트랜잭션 A와 B가 있고, id가 1, 2인 데이터가 있는 상황
- 트랜잭션 A: id 1번 읽고, 2번 변경
- 트랜잭션 B: id 2번 읽고, 1번 변경
- A는 1번, B는 2번에 공유 락 획득 → 서로 상대방이 가진 락이 해제될 때까지 무한 대기 → 데드락 발생

데드락 해결 방법:
- 락 획득 순서를 일관되게 설정 (모든 트랜잭션이 1번 → 2번 순으로 획득)
- 락 타임아웃 설정

---

## MySQL(InnoDB) 갭락/넥스트키락

### 팬텀 리드 (Phantom Read)
트랜잭션이 동일한 조건의 쿼리를 반복 실행할 때, 나중에 실행된 쿼리에서 처음에는 존재하지 않았던 새로운 행이 나타나는 현상입니다.

### 갭락 (Gap Lock)
특정 인덱스 값 사이의 공간을 잠그는 락입니다. 기존 레코드 간의 간격을 보호하여 새로운 레코드의 삽입을 방지합니다.
- 범위 내에 특정 레코드가 존재하지 않을 때 적용
- 팬텀 리드(Phantom Read) 현상을 방지
- 예: 인덱스 값 10과 20 사이의 갭을 잠그면 이 범위 내에 새로운 레코드 15를 추가할 수 없음

```sql
-- id 1, 3, 5가 저장된 orders 테이블

-- 트랜잭션 A: 1-3과 3-5 사이의 갭과 3 레코드 락 설정(넥스트키 락)
START TRANSACTION;
SELECT * FROM orders WHERE orders_id BETWEEN 2 AND 4 FOR UPDATE;

-- 트랜잭션 B: id 4에 데이터 삽입 시도 시, 갭락으로 인해 차단되어 대기
START TRANSACTION;
INSERT INTO orders (orders_id, orders_amount) VALUES (4, 200); -- 대기
```

### 넥스트키 락 (Next-Key Lock)
레코드 락과 갭락을 결합한 형태로, 특정 인덱스 레코드와 그 주변의 갭을 동시에 잠그는 락입니다.
- 레코드 자체의 변경과 주변 공간의 변경을 동시에 제어
- 다른 트랜잭션이 새로운 레코드를 삽입하여 팬텀 리드를 발생시키는 것을 방지

```sql
-- 트랜잭션 A: amount = 200인 레코드에 대한 레코드 락 + 갭락(넥스트키 락) 설정
START TRANSACTION;
SELECT * FROM orders WHERE orders_amount = 200 FOR UPDATE;

-- 트랜잭션 B: orders_amount = 200인 레코드 삽입 시도 시, 넥스트키 락으로 차단
START TRANSACTION;
INSERT INTO orders (orders_id, orders_amount) VALUES (4, 200); -- 대기
```

---

## JPA 락 예시

```java
public interface OrderRepository extends JpaRepository<Order, Long> {

@Transactional
@Lock(value = LockModeType.PESSIMISTIC_READ)
List<Order> findByName(String name);

@Transactional
@Lock(value = LockModeType.PESSIMISTIC_WRITE)
List<Order> findByPriceGreaterThan(int price);

@Transactional
@Lock(value = LockModeType.PESSIMISTIC_FORCE_INCREMENT)
@Query("select o from Order o where o.id = :id")
Optional<Order> findForUpdateWithVersion(@Param("id") Long id);

}
```

---

## 동시성 제어 방법 (MVCC vs Lock-Based)

### MVCC (Multi-Version Concurrency Control)
데이터의 여러 버전을 유지하여 트랜잭션이 동시에 데이터를 읽고 쓸 수 있도록 하는 방식입니다.

특징:
- 각 트랜잭션은 자신만의 일관된 스냅샷(시작 시점)을 기반으로 데이터를 읽음
- 읽기 작업 시 잠금을 사용하지 않아 높은 동시성 제공
- 읽기 중심의 애플리케이션에서 우수한 성능
- 여러 버전의 데이터를 유지하므로 저장 공간이 더 필요
- 갭락과 넥스트키 락을 통해 팬텀 리드 방지

### Lock-Based Concurrency Control
데이터에 접근할 때 잠금(Lock)을 사용하여 동시성을 제어하는 방식입니다.

특징:
- 읽기 작업은 공유 잠금, 쓰기 작업은 배타 잠금 사용
- 다수의 트랜잭션이 동일 데이터에 접근 시 성능 저하 발생 가능
- 잘못된 잠금 순서로 교착 상태(Deadlock) 발생 위험 있음

### MySQL InnoDB에서의 결합 방식
- **읽기 트랜잭션**: MVCC를 사용하여 잠금 최소화, 높은 동시성 유지
- **쓰기 트랜잭션**: 잠금을 사용하여 데이터 일관성과 무결성 유지, 데이터 충돌 방지

---

## 커넥션 풀 (Connection Pool)

### 커넥션 풀이 없을 때의 문제

애플리케이션과 데이터베이스가 통신하기 위해서는 데이터베이스 커넥션이 필요합니다.

데이터베이스 커넥션의 생애주기:
1. 데이터베이스 드라이버를 사용하여 데이터베이스에 연결
2. 데이터 읽기/쓰기를 위한 TCP 소켓 열기
3. 소켓을 통한 데이터 읽기/쓰기
4. 연결 종료
5. 소켓 닫기

커넥션 풀이 없다면 매 요청마다 커넥션을 새로 생성하고 해제하는 과정을 반복해야 합니다. 이 과정은 비용이 많이 들어 응답시간이 길어지며, 동시에 많은 요청이 들어올 경우 데이터베이스의 최대 연결 수를 초과하여 요청이 거부되거나 데이터베이스가 비정상 종료될 수 있습니다.

### 커넥션 풀의 장점

커넥션 풀(Connection Pool)은 애플리케이션과 데이터베이스 간의 연결을 미리 생성해두고 재사용하는 기법입니다.

주요 구성 요소:
- 초기 풀 크기 (Initial Pool Size)
- 최소 풀 크기 (Minimum Pool Size)
- 최대 풀 크기 (Maximum Pool Size)
- 연결 대기 시간 (Connection Timeout)

### 커넥션 풀 사이즈 설정

- 커넥션을 사용하는 주체는 스레드이므로, 커넥션 풀 사이즈와 스레드 풀 사이즈의 균형이 중요
- 커넥션 풀 사이즈 > 스레드 풀 사이즈: 사용되지 못한 커넥션이 리소스 낭비
- 커넥션 풀 사이즈 < 스레드 풀 사이즈: 스레드가 커넥션 반환을 기다려 작업 지연
- 너무 큰 사이즈: 데이터베이스 서버와 애플리케이션 서버의 메모리와 CPU를 과도하게 사용하여 성능 저하

---

## drop / truncate / delete

- `drop`: 테이블 자체 제거
- `truncate`: 데이터 및 일부 메타 초기화, 롤백 불가(일반적으로)
- `delete`: 데이터만 삭제, 조건 기반 처리 가능

---

## 논리 삭제 vs 물리 삭제

### 물리 삭제 (Hard Delete)
DELETE 명령어를 통해 직접 데이터를 삭제하는 방식입니다.

```sql
DELETE FROM member WHERE id = 1;
```

장점:
- 저장 공간 확보
- 테이블 크기 감소로 검색 속도 향상

단점:
- 데이터 복구 어려움
- 삭제된 데이터를 비즈니스 의사결정에 활용하기 어려움

### 논리 삭제 (Soft Delete)
UPDATE 명령을 사용하여 삭제 여부를 나타내는 컬럼을 수정하는 방식입니다. 실제로는 데이터가 삭제되지 않고 삭제되었음을 표시만 합니다.

```sql
-- 논리 삭제 처리
UPDATE member SET deleted_at = CURDATE() WHERE id = 1;

-- 논리 삭제된 데이터 제외 조회
SELECT * FROM member WHERE deleted_at IS NULL;
```

장점:
- 데이터 복구 용이
- 삭제된 데이터를 비즈니스 의사결정에 활용 가능

단점:
- 테이블에 데이터가 많아져 성능에 악영향을 줄 수 있음
- 논리 삭제된 데이터를 제외하지 않고 조회하는 실수가 발생할 수 있음

---

## NOT IN 쿼리 성능 문제

`NOT IN`을 사용한 쿼리는 직관적이지만 대규모 데이터셋에서 심각한 성능 저하를 일으킬 수 있습니다.

### 문제점
1. 대부분의 DBMS에서 전체 테이블 스캔이나 인덱스 풀 스캔을 유발
2. `IN` 절은 인덱스 Range Scan을 활용하지만, `NOT IN`은 인덱스 활용도가 현저히 떨어짐
3. 대량의 값을 `IN` 절에 넣으면 파싱 및 최적화 단계에서 추가 오버헤드 발생
4. NULL 값 처리 로직으로 인한 예상치 못한 결과 발생 (`column NOT IN (1, 2, NULL)`은 항상 빈 결과 반환)

### 최적화 방안

#### 1. NOT EXISTS 활용
```sql
SELECT p FROM Post p
WHERE NOT EXISTS (
    SELECT 1 FROM Post temp
    WHERE temp.id = p.id AND temp.id IN :postIds
)
```
행 단위로 평가되어 매칭되는 첫 행을 찾자마자 평가를 중단합니다. 대규모 데이터셋에서 가장 안정적이고 확장성 있는 성능을 제공합니다.

#### 2. LEFT JOIN + IS NULL 패턴
```sql
SELECT p FROM Post p
LEFT JOIN (
    SELECT temp.id FROM Post temp WHERE temp.id IN :postIds
) filtered ON p.id = filtered.id
WHERE filtered.id IS NULL
```
서브쿼리 결과가 작을 때 특히 효율적이며, PK 인덱스를 사용한 JOIN 연산이 최적화됩니다.

---

## Statement vs PreparedStatement

JDBC에서 Statement와 PreparedStatement는 모두 SQL 실행을 담당하지만, 사용 방식과 성능, 보안 측면에서 차이가 있습니다.

### Statement
문자열 연결을 이용해 SQL을 동적으로 구성합니다.

```java
Statement stmt = conn.createStatement();
ResultSet rs = stmt.executeQuery("SELECT * FROM users WHERE age > 30");
```

단점:
- SQL 인젝션 공격에 취약
- 매번 쿼리 파싱 필요 → 성능 저하

### PreparedStatement
동적으로 파라미터를 바인딩할 수 있는 기능을 제공합니다.

```java
String sql = "SELECT * FROM users WHERE age > ?";
PreparedStatement pstmt = conn.prepareStatement(sql);
pstmt.setInt(1, 30);
ResultSet rs = pstmt.executeQuery();
```

장점:
- 값을 바인딩하면 내부적으로 이스케이프 처리 → SQL 인젝션 방지
- SQL 구문 분석 결과를 캐싱할 수 있어 반복 실행 시 성능 우수

---

## SQL 인젝션 (SQL Injection)

**SQL 인젝션(SQL Injection)** 은 웹 애플리케이션에서 사용자의 입력값이 SQL 쿼리에 안전하게 처리되지 않을 때 발생하는 보안 취약점입니다.

### 공격 예시

```java
// 취약한 코드
String sql = "SELECT * FROM users WHERE username = '" + username + "' AND password = '" + password + "'";
```

사용자가 `username: admin' --` 을 입력하면:
```sql
-- 생성되는 쿼리 (비밀번호 조건이 주석 처리됨)
SELECT * FROM users WHERE username = 'admin' -- ' AND password = '1q2w3e4r!';
```

공격에 사용되는 주요 페이로드:
- `' OR '1'='1`: 항상 참이 되는 조건
- `' UNION SELECT * FROM accounts --`: 다른 테이블의 정보 조회
- `'; DROP TABLE users; --`: 테이블 삭제

### 방지 방법

1. **PreparedStatement 사용**: placeholder(`?`)에 값을 바인딩하고 내부적으로 이스케이프 처리

```java
String sql = "SELECT * FROM users WHERE username = ? AND password = ?";
PreparedStatement pstmt = conn.prepareStatement(sql);
pstmt.setString(1, username);
pstmt.setString(2, password);
```

2. **ORM 프레임워크 사용**: JPA, Hibernate와 같은 ORM은 SQL을 직접 작성하지 않아도 됨
3. **입력값 검증**: 공격에 사용되는 SQL 구문의 포함 여부를 검증
4. **최소 권한 부여**: 데이터베이스 계정에 최소한의 권한만 부여
5. **오류 메시지 숨김**: SQL 오류나 예외 메시지를 사용자에게 직접 노출하지 않음

---

## 참고 링크

- [MySQL 8.0의 공유 락(Shared Lock)과 배타 락(Exclusive Lock)](https://hudi.blog/mysql-8.0-shared-lock-and-exclusive-lock/)
- [JPA의 비관적 락, MySQL 8.0 공유락과 베타락을 통한 동시성 제어](https://haon.blog/haon/jpa/pemistic-lock/)
- [Shared and Exclusive Locks (MySQL 공식 문서)](https://dev.mysql.com/doc/refman/8.0/en/innodb-locking.html#innodb-shared-exclusive-locks)
- [MySQL - InnoDB Locking](https://dev.mysql.com/doc/refman/8.0/en/innodb-locking.html)
- [MySQL - Transaction Isolation Levels](https://dev.mysql.com/doc/refman/8.0/en/innodb-transaction-isolation-levels.html)
- [스토리지 엔진 수준의 락의 종류(레코드 락, 갭 락, 넥스트 키 락)](https://mangkyu.tistory.com/298)
- [MVCC(다중 버전 동시성 제어)란?](https://mangkyu.tistory.com/53)
- [HikariCP](https://github.com/brettwooldridge/HikariCP)
- [데이터베이스 커넥션 풀과 HikariCP](https://hudi.blog/dbcp-and-hikaricp/)
- [JPA에서 Soft Delete를 구현하는 방법](https://engineerinsight.tistory.com/172)
- [PreparedStatement란?](https://umbum.dev/580/)
- [Oracle Java Tutorial - Using Prepared Statements](https://docs.oracle.com/javase/tutorial/jdbc/basics/prepared.html)
- [DB인덱스](https://www.maeil-mail.kr/question/60)
- [데이터베이스 시스템에서 동시성을 제어하는 방법](https://www.maeil-mail.kr/question/92)
