### [TRANSACTION](db_transaction.md)
### [mySQL](mysql.md)
### [redis](redis.md)

- [DB인덱스](https://www.maeil-mail.kr/question/60)
- [공유락,배타락](https://www.maeil-mail.kr/question/80)
- [데이터베이스 시스템에서 동시성을 제어하는 방법에 대해 설명](https://www.maeil-mail.kr/question/92)
- drop, truncate, delete
  - drop: 테이블까지 삭제. 롤백 가능
  - truncate: 테이블 초기화. 데이터 삭제 및 자동증가값등의 초기화 수행. 
    - 내부적으로는 테이블을 재설정 하는 방식
    - 롤백 불가
    - fk가 걸려있는 테이블에서 사용불가
  - delete: 테이블의 데이터만 삭제
    - 롤백가능