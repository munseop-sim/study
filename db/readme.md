### [TRANSACTION](db_transaction.md)
### [mySQL](mysql.md)
### [redis](redis.md)

### DB Lock
- DBMS 측면에서의 Lock
  - Shared Lock
    - 읽기전용 Lock
    - 해당 Lock이 걸린 데이터에 대해서 SharedLock은 가능하지만, Exclusive Lock은 불가
    - JPA(해당 트랜잭션에 아래와 같이 선언)
      ```java
        @Transactional
        @Lock(value = LockModeType.PESSIMISTIC_READ)
        public int order(String name, int price);
      ```
  - Exclusive Lock
    - 쓰기전용 Lock
    - 해당 락이 걸린 데이터에 대해서는 ExclusiveLock, SharedLock 획득 불가
    - 데드락 발생가능
      - 해결방법
        - lock timeout설정
        - 데이터 처리 요청단위 일관되게 처리(트랜잭션 Lock순서를 일관되게 한다.)
        - DBMS 데드락 감지 기능 사용
    - JPA(해당 트랜잭션에 아래와 같이 선언)
      ```java
        @Transactional
        @Lock(value = LockModeType.PESSIMISTIC_WRITE)
        public int order(String name, int price);
        
        //비관적 락이지만 버전 정보를 강제적으로 증가
        @Transactional
        @Lock(value = LockModeType.PESSIMISTIC_FORCE_INCREMENT)
        public int order(String name, int price);
      ```
  - 트랜잭션 격리수준과 락의 차이: 
    - 격리수준은 해당 트랜잭션이 다른 트랜잭션에서 변경한 데이터를 볼 수 있는 기준을 정의한 것.
    - 락(Lock) 은 다른 트랜잭션에서 해당 데이터에 접근하는 것을 막는 기능을 수행하는 것.
      
### drop, truncate, delete
  - drop: 테이블까지 삭제. 롤백 가능
  - truncate: 테이블 초기화. 데이터 삭제 및 자동증가값등의 초기화 수행. 
    - 내부적으로는 테이블을 재설정 하는 방식
    - 롤백 불가
    - fk가 걸려있는 테이블에서 사용불가
  - delete: 테이블의 데이터만 삭제
    - 롤백가능

### mySQL(innoDB) 갭락, 넥스트키락
#### 갭락
- 갭 락은 특정 인덱스 값 사이의 공간을 잠그는 락
- 기존 레코드 간의 간격을 보호하여 새로운 레코드의 삽입을 방지
- 갭 락은 범위 내에 특정 레코드가 존재하지 않을 때 적용
- 트랜잭션이 특정 범위 내에서 데이터의 삽입을 막아 팬텀 읽기([Phantom Read](db_transaction.md#관련-용어)) 현상을 방지
- 예를 들어, 인덱스 값 10과 20 사이의 갭을 잠그면 이 범위 내에 새로운 레코드 15를 추가할 수 없다.
```sql
-- id 1, 3, 5가 저장된 orders 테이블

-- 트랜잭션 A 시작
START TRANSACTION;

-- 트랜잭션 A 1-3과 3-5 사이의 갭과 3 레코드 락 설정(넥스트키 락)
SELECT * FROM orders WHERE orders_id BETWEEN 2 AND 4 FOR UPDATE;

-- 트랜잭션 B 시작
START TRANSACTION;

-- 트랜잭션 B가 id 4에 데이터 삽입 시도 시, 갭락으로 인해 삽입이 차단되어 대기
INSERT INTO orders (orders_id, orders_amount) VALUES (4, 200);
```
#### 넥스트락
- 레코드 락과 갭락을 결합한 형태
- 특정 인덱스 레코드와 그 주변의 갭을 동시에 잠그는 락
- 이를 통해 레코드 자체의 변경과 함께 그 주변 공간의 변경도 동시에 제어가능
- 넥스트키 락은 특정 레코드와 그 주변 공간을 잠그기 때문에, 다른 트랜잭션이 새로운 레코드를 삽입하여 [Phantom Read](db_transaction.md#관련-용어)를 발생시키는 것을 방지
```sql
-- 트랜잭션 A 시작
START TRANSACTION;

-- 트랜잭션 A amount = 200인 orders_id = 2 레코드에 대한 레코드 락과 1-2, 2-3에 대한 갭락을 동시에 잠금으로써 넥스트키 락을 설정
SELECT * FROM orders WHERE orders_amount = 200 FOR UPDATE;

-- 트랜잭션 B 시작
START TRANSACTION;

-- 트랜잭션 B orders_id = 4, orders_amount = 200인 레코드 삽입 시도 시, 넥스트키 락으로 인해 차단되어 대기
INSERT INTO orders (orders_id, order_amount) VALUES (4, 200);
...
```

- [DB인덱스](https://www.maeil-mail.kr/question/60)
- [데이터베이스 시스템에서 동시성을 제어하는 방법에 대해 설명](https://www.maeil-mail.kr/question/92)