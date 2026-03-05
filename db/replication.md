# DB Replication

## DB Replication이란?

DB Replication은 데이터베이스의 고가용성과 데이터 안정성을 보장하기 위해 널리 활용되는 핵심 기술입니다. 원본(Source) 서버와 복제(Replica) 서버 간의 데이터 동기화를 통해 데이터의 지속적인 가용성과 신뢰성을 유지합니다.

- Source 서버에서 발생하는 모든 데이터 변경 사항을 Replica 서버로 복제하여 두 서버 간의 데이터 일관성을 유지
- 주로 Binary Log를 기반으로 이루어짐

---

## Binary Log 기반 복제

Binary log는 Source 서버에서 실행된 모든 데이터 변경 쿼리를 기록하는 역할을 합니다. MySQL에서는 Binary log를 저장하는 방식으로 **Row**, **Statement**, **Mixed**의 세 가지 방식을 제공합니다.

### Row 방식
데이터베이스의 각 행별로 변경된 내용을 정확히 기록합니다.

- 장점: 데이터 일관성을 매우 높게 유지 가능. 특정 행의 이전 상태와 변경된 상태를 모두 기록하므로, Replica 서버에서도 Source 서버와 동일한 데이터 상태를 유지
- 단점: 모든 행의 변경 사항을 저장하기 때문에 Binary log 파일의 크기가 급격히 증가할 수 있음

### Statement 방식
데이터 변경을 일으킨 SQL 문 자체를 Binary log에 기록합니다.

- 장점: 로그 파일의 크기를 상대적으로 작게 유지하여 저장 공간을 절약
- 단점: `SELECT NOW()`와 같이 실행할 때마다 다른 값을 반환하는 비확정적(non-deterministic) SQL 쿼리가 실행될 경우, Source와 Replica 서버에서 다른 결과를 초래하여 데이터 불일치 문제 발생 가능

### Mixed 방식
상황에 따라 Row 기반과 Statement 기반을 혼합하여 로그를 기록합니다.

- 비확정적 SQL이 아닌 경우: Statement 방식으로 저장 공간 절약
- 비확정적 SQL인 경우: Row 방식으로 데이터 일관성 유지
- 장점: 두 방식의 장점을 모두 활용하여 데이터 불일치 문제 최소화
- 단점: 구현이 다소 복잡할 수 있음

---

## 복제 과정

```
Source 서버
    ↓ (데이터 변경 쿼리 실행)
Binary Log 기록
    ↓ (IO Thread가 읽어서 전송)
Replica 서버의 Relay Log
    ↓ (SQL 스레드가 적용)
Replica 서버 실제 데이터베이스 반영
```

1. Source 서버에서 데이터 변경 쿼리가 실행되고 선택된 로그 저장 방식에 따라 Binary log에 기록
2. Replica 서버의 **IO Thread**가 Source 서버의 Binary log를 읽어와 Replica 서버의 **Relay log**로 전송
3. Relay log: Replica 서버에서 Source 서버의 Binary log를 저장하는 임시 저장소
4. Replica 서버의 **SQL 스레드**가 Relay log를 기반으로 실제 데이터베이스에 변경 사항을 적용

일반적으로 약 100밀리초 이내에 데이터 동기화가 완료됩니다.

---

## 읽기/쓰기 분리 (Read/Write Separation)

DB Replication의 주요 활용 방법 중 하나는 읽기/쓰기 분리입니다.

- **Source(Master) 서버**: 쓰기(INSERT, UPDATE, DELETE) 전담
- **Replica(Slave) 서버**: 읽기(SELECT) 전담

### 장점
- 읽기 부하를 여러 Replica 서버에 분산하여 성능 향상
- Source 서버 장애 시 Replica 서버를 빠르게 승격하여 고가용성 확보
- 데이터 백업을 Replica 서버에서 수행하여 Source 서버 부하 감소

### 고려사항
- Replication은 비동기 방식으로 이루어지므로 일시적인 데이터 불일치(Replication Lag) 발생 가능
- 강한 일관성이 필요한 경우 Source 서버에서 직접 읽기 수행 필요
- Spring에서는 `@Transactional(readOnly = true)` + HikariCP DataSource Routing으로 구현 가능

---

## 참고 링크

- [MySQL Replication 공식 문서](https://dev.mysql.com/doc/refman/8.0/en/replication.html)
- [MySQL Binary Log 공식 문서](https://dev.mysql.com/doc/refman/8.0/en/binary-log.html)
- [DB Replication 개념 정리](https://hudi.blog/db-replication/)
