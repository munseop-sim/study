# NoSQL & 분산 데이터베이스

## 관계형 DB vs 비관계형 DB

### 관계형 데이터베이스 (RDB)
고정된 로우와 컬럼으로 구성된 테이블에 데이터를 저장하며, SQL을 사용하여 여러 테이블을 조인할 수 있습니다.

특징:
- 데이터를 중복 없이 한 번만 저장하고 데이터 무결성을 보장
- 스케일 업(Scale Up) 방식으로 확장
- 트랜잭션과 복잡한 쿼리, 데이터 무결성과 일관성이 중요한 경우 적합

한계:
- 스키마를 유연하게 바꾸기 어려움
- 복잡한 관계가 생기면 복잡한 쿼리가 필요해짐

대표 DB: MySQL, PostgreSQL, Oracle, SQL Server

### 비관계형 데이터베이스 (NoSQL)
정해진 스키마가 존재하지 않으며 자유롭게 데이터를 저장하고 조회할 수 있습니다.

특징:
- 대량의 데이터와 높은 사용자 부하에서도 손쉽게 수평 확장(Scale Out) 가능
- 아주 낮은 응답 지연시간이 필요한 경우 적합
- 스키마가 빈번히 변경되는 경우 유연하게 대응 가능

단점:
- 중복을 허용하므로 데이터의 일관성이 저하되며 용량이 증가

### 선택 기준

| 상황 | 추천 |
|---|---|
| 데이터가 구조화되어 있고 자주 변경되지 않음 | RDB |
| 트랜잭션과 복잡한 쿼리가 필요 | RDB |
| 데이터 무결성과 일관성이 중요 | RDB |
| 아주 낮은 응답 지연시간이 필요 | NoSQL |
| 스키마가 빈번히 변경됨 | NoSQL |
| 아주 많은 양의 데이터를 저장해야 함 | NoSQL |

---

## NoSQL 유형

### 1. 키-값 데이터베이스 (Key-Value Database)
키를 고유한 식별자로 사용하는 키-값 쌍의 형태로 데이터를 저장합니다.

- 특징: 구조가 단순하고 빠른 읽기 및 쓰기 성능 제공
- 대표 DB: **Redis**, Amazon DynamoDB
- 사용 사례: 세션 저장, 캐시, 실시간 순위

### 2. 문서 지향 데이터베이스 (Document-oriented Database)
JSON, BSON, XML 등의 형식으로 데이터를 저장합니다.

- 특징: 유연한 스키마, 복잡한 데이터 구조를 쉽게 표현
- 대표 DB: **MongoDB**, CouchDB
- 사용 사례: 콘텐츠 관리 시스템, 사용자 프로필 저장

### 3. 열 지향 데이터베이스 (Column Family Database)
데이터를 열 단위로 저장합니다.

- 특징: 대량의 데이터 처리에 적합, 행마다 각기 다른 수의 열과 여러 데이터 유형을 가질 수 있음
- 대표 DB: **Apache Cassandra**, HBase
- 사용 사례: 대규모 데이터 분석, 로그 수집

### 4. 그래프 데이터베이스 (Graph Database)
노드, 엣지 구조로 구성된 그래프로 데이터를 저장합니다.

- 특징: 복잡한 관계를 표현하는 데 사용, 레이블(그룹화된 노드)을 통해 쿼리를 효율적으로 실행
- 대표 DB: **Neo4j**, Amazon Neptune
- 사용 사례: 소셜 네트워크 분석, 추천 시스템

### 5. 시계열 데이터베이스 (Time Series Database)
시간에 따라 변화하는 데이터를 저장합니다.

- 특징: 타임스탬프가 있는 메트릭, 이벤트 등을 처리하기 위해 사용, 시간 경과에 따른 변화 측정에 최적화
- 대표 DB: **InfluxDB**, Prometheus, TimescaleDB
- 사용 사례: IoT 데이터 수집, 금융 데이터 분석

---

## CAP 이론

CAP 정리는 분산 데이터베이스 시스템이 CAP 중 **2개의 속성만을 제공할 수 있다**는 이론입니다.

### 3가지 속성

- **일관성(Consistency)**: 모든 클라이언트 요청은 어느 노드에 연결되어도 같은 데이터를 볼 수 있음
- **가용성(Availability)**: 노드 일부에 문제가 발생하여도 시스템은 클라이언트의 모든 요청에 유효한 응답을 전해줄 수 있어야 함
- **분할 내성(Partition Tolerance)**: 노드 사이에 통신이 불가능한 상황(파티션)에서도 시스템이 계속 동작함

### 속성의 조합

3개의 분산 데이터베이스가 존재하는 경우, A 파티션과 B 파티션으로 네트워크가 분할되면 파티션 간 노드들은 서로 통신할 수 없어 데이터 전파가 불가능합니다.

| 조합 | 설명 |
|---|---|
| **CA** | 일관성 + 가용성. 네트워크 장애는 피할 수 없으므로 분산 시스템에서 분할 내성 희생은 현실적으로 불가능 → 실제로 존재하지 않음 |
| **CP** | 일관성 + 분할 내성. 파티션 해결 전까지 다른 DB의 연산을 중단하여 일관성 유지, 가용성 희생 |
| **AP** | 가용성 + 분할 내성. 파티션 상황에서도 읽기/쓰기 중단하지 않음. 일관성 희생, 최종적 일관성으로 보완 |

---

## 열 기반 DB vs 행 기반 DB

### 행 기반 데이터베이스 (Row-oriented Database)
데이터를 행 단위로 관리하는 DBMS입니다.

- 행 단위 읽기 및 쓰기 연산에 최적화
- OLTP(온라인 트랜잭션 처리)에 적합
- 대표 DB: **MySQL**, **PostgreSQL**

```
저장 방식: [Atom, 2024-01-23] [Prin, 2024-02-01] [Gosmdochee, 2024-02-03]
```

### 열 기반 데이터베이스 (Column-oriented Database)
열 기반으로 데이터를 관리하는 DBMS입니다.

- 데이터 조회 시 필요한 열만 로드하여 디스크 I/O 감소
- 같은 종류의 데이터가 연속적으로 저장되므로 압축 효율이 높음
- OLAP(온라인 분석 처리)에 적합
- 대표 DB: **BigQuery**, **Redshift**, Snowflake

```
저장 방식: [Atom, Prin, Gosmdochee] [2024-01-23, 2024-02-01, 2024-02-03]
```

### 비교

| 항목 | 행 기반 | 열 기반 |
|---|---|---|
| 저장 방식 | 행 단위 | 열 단위 |
| 적합한 워크로드 | OLTP (트랜잭션 처리) | OLAP (분석) |
| 특정 행 읽기 | 빠름 | 느림 |
| 특정 열 집계 | 느림 | 빠름 |
| 압축 효율 | 낮음 | 높음 (같은 타입 데이터가 연속) |
| 대표 DB | MySQL, PostgreSQL | BigQuery, Redshift, Snowflake |

---

## RDB 페이징 방법

### 페이징 쿼리란?
전체 데이터를 부분적으로 나누어 데이터를 조회하거나 처리할 때 사용합니다. 리소스 사용 효율 증가 및 로직 처리 시간을 단축시킵니다.

### 1. LIMIT/OFFSET 방식

MySQL에서 일반적으로 사용하는 페이징 방법입니다.

```sql
SELECT *
FROM subscribe
LIMIT 500
OFFSET 0;
```

단점:
- 뒤에 있는 데이터를 읽을수록 점점 응답 시간이 길어짐
- DBMS는 지정된 OFFSET 수만큼 모든 레코드를 읽은 이후에 데이터를 가져오기 때문
- 예: OFFSET 100000이면 100000개의 레코드를 읽고 버린 뒤 데이터를 가져옴

### 2. Cursor 기반 페이징 (No-Offset 방식)
이전 페이지의 마지막 데이터 값을 기반으로 다음 페이지를 조회합니다.

```sql
-- 인덱스 생성
CREATE TABLE subscribe (
   id INT NOT NULL AUTO_INCREMENT,
   deleted_at DATETIME NULL,
   created_at DATETIME NOT NULL,
   PRIMARY KEY(id),
   KEY idx_deleted_at_id(deleted_at, id)
);

-- 첫 페이지 조회
SELECT *
FROM subscribe
WHERE
    deleted_at >= ? AND deleted_at < ?
ORDER BY deleted_at, id
LIMIT 10;

-- N회차 페이지 조회 (이전 페이지의 마지막 값 기반)
-- 예: 이전 페이지의 마지막 deleted_at = '2024-01-01', id = 78
SELECT *
FROM subscribe
WHERE
   -- deleted_at이 같은 케이스를 대응
   (deleted_at = '2024-01-01 00:00:00' AND id > 78) OR
   -- 마지막 데이터 이후 데이터 조회
   (deleted_at > '2024-01-01 00:00:00' AND deleted_at < ?)
ORDER BY deleted_at, id
LIMIT 10;
```

장점:
- 인덱스를 효율적으로 활용하여 일정한 성능 유지
- OFFSET 방식과 달리 앞의 데이터를 읽고 버리지 않음

단점:
- 첫 페이지와 이후 페이지의 쿼리 구조가 다름
- 특정 페이지로 바로 이동하는 것이 어려움

---

## 참고 링크

- [AWS - Types of NoSQL databases](https://docs.aws.amazon.com/whitepapers/latest/choosing-an-aws-nosql-database/types-of-nosql-databases.html)
- [MongoDB - What Is NoSQL?](https://www.mongodb.com/ko-kr/resources/basics/databases/nosql-explained)
- [기계인간 John Grib - CAP 정리](https://johngrib.github.io/wiki/jargon/cap-theorem/)
- [Data School - Row vs Column Oriented Databases](https://dataschool.com/data-modeling-101/row-vs-column-oriented-databases/)
- [Amazon Redshift - 열 기반 스토리지](https://docs.aws.amazon.com/ko_kr/redshift/latest/dg/c_columnar_storage_disk_mem_mgmnt.html)
- [페이징 성능 개선하기 - No Offset 사용하기](https://jojoldu.tistory.com/528)
- [페이징 성능 개선하기 - 커버링 인덱스 사용하기](https://jojoldu.tistory.com/529?category=637935)
- [SQL vs NoSQL 파헤치기](https://maily.so/devpill/posts/wjzded2yo3p)
