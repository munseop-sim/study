# transaction isolation level
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
