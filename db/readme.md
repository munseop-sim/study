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




- [DB인덱스](https://www.maeil-mail.kr/question/60)
- [데이터베이스 시스템에서 동시성을 제어하는 방법에 대해 설명](https://www.maeil-mail.kr/question/92)