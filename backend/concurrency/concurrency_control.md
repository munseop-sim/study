# 동시성 제어
1. RDB
   1. Lock
      1. 낙관적 락(Optimistic Lock) &rarr; SW 레벨 : `@Vesion` 등
      2. 비관적락 (Pessimistic Lock) &rarr; RDB 레벨 [DBMS Lock](../../db/README.md#db-lock)
   2. Isolation Level 지정
      - READ UNCOMMITED : 다른 트랜잭션에 수행중인 변경 사항을 읽을 수 있음
      - READ COMMITED : 다른 트랜잭션이 커밋한 데이터만 읽을 수있음
      - REPEATABLE READ : 트랜잭션 내에서 같은 데이터를 다시 읽을 때 동일한 값 유지
      - SERIALIZABLE : 가장 엄격한 격리수준
2. Redis
    - `setNx`명령어 사용하여 분산락을 활용하여 원척적으로 서비스를 호출할 수 없도록 차단
      - redis 는 Single Thread 기반으로 운영되기 때문에 동시성제어에 효과적 
3. Spring
    1. JPA
       - `@Version` Annotation 사용 &rarr; Optimistic Lock
4. 메세지 큐 활용
    - RabbitMQ, Kafka 등을 활용하여 순차적으로 처리되도록 한다.