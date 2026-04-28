# Database

데이터베이스 학습 내용을 주제별로 분류한 인덱스입니다.

## 📚 Contents

### Core
- [db_transaction.md](./db_transaction.md): 트랜잭션 기초 (ACID, 격리 수준)
- [locking_and_sql_ops.md](./locking_and_sql_ops.md): 락, DDL/DML 차이, InnoDB 갭락/넥스트키락
- [mysql_gap_lock_and_phantom.md](./mysql_gap_lock_and_phantom.md): MySQL InnoDB Gap Lock / Next-Key Lock 심화 (`locking_and_sql_ops.md`의 갭락 개념 보완 문서)
- [mysql_index_query_optimization.md](./mysql_index_query_optimization.md): MySQL 인덱스 설계, 실행 계획, 커버링 인덱스, 페이지네이션 최적화

### Engines / Stores
- [mysql.md](./mysql.md)
- [redis.md](./redis.md): Redis 자료구조, 트랜잭션, Pub/Sub, Replication, 모니터링, 싱글 스레드 이벤트 루프, KEYS/SCAN 운영 기준

## 분류 기준
- DB 엔진/트랜잭션/락: `db`
- 애플리케이션 레벨 동시성 제어: `backend/concurrency`
- 캐시 설계/장애 대응 전략: `backend/system-design`
