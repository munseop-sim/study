## mySQL
default connection max size : 150

```sql
show status like 'Threads_connected'; # 쓰레드_커넥션_확인
show variables like 'max_connections'; # 맥스_쓰레드_커넥션_확인 
set global max_connections=200; # 맥스_커넥션_수_조정
```

default wait timeout : 28800초 (8시간)
> wait_timeout : 활동하지 않는 커넥션을 끊을때까지 서버가 대기하는 시간
```sql
show global variables like 'wait_timeout'; #설정_확인
set global wait_timeout=28800; #설정_변경
```

default interactive_timeout : 28800초 (8시간)
> interactive_timeout  :  활동중인 커넥션이 닫히기 전까지 서버가 대기하는 시간
```sql
show variables like 'interactive%'; #설정_확인
set global interactive_timeout=28800; #설정_변경
```
mysql에서 wait timeout, interactivce_time의 경우 운영하는 시스템에 따라 설정해야 한다고 함.  
참고로 PHP의 경우 60초 정도로 설정.


## Lock
### Shared Lock
- 데이터를 읽기 위해 사용되는 Lock, 다른 트랜잭션에서 읽기는 가능하지만 쓰기는 불가능
### Exclusive Lock
- 데이터를 쓰기 위해 사용되는 Lock, 배타적 락이 설정된 경우에는 다른 트랜잭션에 읽기/쓰기 불가

## 엔진종류
- MyISAM
  - 트랜잭션 불가
  - table lock
  - 대량의 데이터 삽입/갱신시에 유리
  - 동시성 저하
- InnoDB
  - 트랜잭션 가능
  - row lock
  - 동시성에 유리