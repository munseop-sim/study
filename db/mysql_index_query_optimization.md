# MySQL 인덱스와 쿼리 최적화

> **관련 문서**
> - [MySQL 기본](./mysql.md)
> - [DB 락과 SQL 연산](./locking_and_sql_ops.md)
> - [MySQL Gap Lock / Next-Key Lock](./mysql_gap_lock_and_phantom.md)

---

## 1. 인덱스의 목적

인덱스는 데이터를 빠르게 찾기 위한 정렬된 자료구조다.
MySQL InnoDB의 일반적인 인덱스는 B+Tree 기반이며, 검색 조건을 정렬된 키 범위로 줄여서 디스크 접근량을 감소시킨다.

인덱스는 읽기를 빠르게 하지만 쓰기 비용을 증가시킨다.

| 작업 | 인덱스 영향 |
|---|---|
| SELECT | 조건에 맞는 행을 빠르게 탐색 |
| INSERT | 인덱스에도 새 키를 추가해야 함 |
| UPDATE | 인덱스 컬럼 변경 시 인덱스 재정렬 필요 |
| DELETE | 인덱스 엔트리 삭제 필요 |

인덱스는 "많이 만들수록 좋은 것"이 아니라, 자주 실행되는 조회 패턴을 기준으로 최소한으로 설계한다.

---

## 2. 클러스터드 인덱스와 세컨더리 인덱스

### 클러스터드 인덱스

InnoDB 테이블은 기본 키를 기준으로 데이터가 정렬되어 저장된다.
즉, PK 인덱스의 리프 노드가 실제 데이터 페이지다.

```sql
CREATE TABLE wallet (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    balance BIGINT NOT NULL
);
```

`WHERE id = ?` 조회는 PK B+Tree에서 바로 데이터 페이지를 찾는다.

### 세컨더리 인덱스

세컨더리 인덱스의 리프 노드는 실제 행 전체가 아니라 **PK 값**을 가진다.

```sql
CREATE INDEX idx_wallet_user_id ON wallet(user_id);
```

`WHERE user_id = ?` 조회 흐름:

```
1. idx_wallet_user_id에서 user_id 검색
2. 리프 노드에서 PK(id) 획득
3. PK 클러스터드 인덱스로 다시 조회
```

이를 MySQL/InnoDB 문맥에서는 보통 **clustered index lookup**, **row lookup** 또는 **back to table**이라고 부른다.
`bookmark lookup`은 SQL Server 문맥에서 주로 쓰이는 용어다.
조회 컬럼이 모두 세컨더리 인덱스에 있으면 이 추가 조회를 피할 수 있다.

---

## 3. 복합 인덱스 설계

복합 인덱스는 컬럼 순서가 핵심이다.

```sql
CREATE INDEX idx_orders_user_status_created
ON orders(user_id, status, created_at);
```

이 인덱스는 왼쪽부터 순서대로 사용할 수 있다.

| 조건 | 사용 가능 여부 |
|---|---|
| `WHERE user_id = ?` | 가능 |
| `WHERE user_id = ? AND status = ?` | 가능 |
| `WHERE user_id = ? AND status = ? AND created_at BETWEEN ? AND ?` | 가능 |
| `WHERE status = ?` | 불리함 |
| `WHERE created_at BETWEEN ? AND ?` | 불리함 |

### Equality before Range

복합 인덱스는 보통 다음 순서로 설계한다.

```
동등 조건(=, IN) -> 범위 조건(BETWEEN, >, <) -> 정렬/커버링 컬럼
```

예:

```sql
SELECT *
FROM orders
WHERE user_id = ?
  AND status = ?
  AND created_at >= ?
ORDER BY created_at DESC
LIMIT 20;
```

적합한 인덱스:

```sql
CREATE INDEX idx_orders_user_status_created
ON orders(user_id, status, created_at DESC);
```

주의: MySQL 8.0부터는 descending index가 실제 내림차순 키 파트로 저장된다.
MySQL 8.0 미만에서는 `DESC` 지정이 무시되거나 ASC처럼 처리되므로, 버전에 따라 실행 계획을 확인해야 한다.

범위 조건이 등장하면 그 뒤 컬럼은 탐색 범위를 줄이는 데 제한적으로만 쓰인다.

---

## 4. Covering Index

커버링 인덱스는 쿼리에 필요한 컬럼을 인덱스만으로 모두 충족하는 경우다.

```sql
SELECT id, status, created_at
FROM orders
WHERE user_id = ?
ORDER BY created_at DESC
LIMIT 20;
```

```sql
CREATE INDEX idx_orders_user_created_status
ON orders(user_id, created_at DESC, status);
```

MySQL 8.0 미만에서는 descending index 동작이 제한적이므로 `ORDER BY created_at DESC` 최적화 여부를 `EXPLAIN`으로 확인한다.

`id`는 InnoDB 세컨더리 인덱스 리프에 PK로 포함되므로, 위 쿼리는 인덱스만 읽고 응답할 수 있다.

장점:
- 테이블 데이터 페이지 접근 감소
- 대량 조회에서 I/O 절감
- 페이지네이션 성능 개선

단점:
- 인덱스 크기 증가
- 쓰기 비용 증가
- 너무 넓은 인덱스는 버퍼 풀 효율 저하

---

## 5. 실행 계획 확인

```sql
EXPLAIN
SELECT *
FROM orders
WHERE user_id = 10
  AND status = 'PAID'
ORDER BY created_at DESC
LIMIT 20;
```

주요 컬럼:

| 항목 | 의미 | 주의 |
|---|---|---|
| `type` | 접근 방식 | `ALL`이면 풀 스캔 가능성 |
| `key` | 실제 사용 인덱스 | 기대한 인덱스인지 확인 |
| `rows` | 예상 스캔 행 수 | 실제 데이터 분포와 다를 수 있음 |
| `Extra` | 부가 작업 | `Using filesort`, `Using temporary` 주의 |

접근 타입은 대략 다음 순서로 좋다.

```
const -> eq_ref -> ref -> range -> index -> ALL
```

`Using filesort`가 항상 나쁜 것은 아니지만, 대량 데이터에서 정렬 비용이 커질 수 있다.
정렬 조건까지 인덱스 순서에 맞추면 filesort를 줄일 수 있다.

---

## 6. 페이지네이션 최적화

### OFFSET 방식의 문제

```sql
SELECT *
FROM orders
WHERE user_id = ?
ORDER BY created_at DESC
LIMIT 20 OFFSET 100000;
```

MySQL은 앞의 100000개를 건너뛰기 위해 실제로 읽고 버린다.
뒤 페이지로 갈수록 느려진다.

### Keyset Pagination

마지막으로 본 키를 기준으로 다음 페이지를 조회한다.

```sql
SELECT *
FROM orders
WHERE user_id = ?
  AND created_at < ?
ORDER BY created_at DESC
LIMIT 20;
```

동일한 `created_at`이 있을 수 있으므로 PK를 tie-breaker로 함께 둔다.

```sql
SELECT *
FROM orders
WHERE user_id = ?
  AND (
      created_at < ?
      OR (created_at = ? AND id < ?)
  )
ORDER BY created_at DESC, id DESC
LIMIT 20;
```

```sql
CREATE INDEX idx_orders_user_created_id
ON orders(user_id, created_at DESC, id DESC);
```

---

## 7. 인덱스를 못 타는 패턴

```sql
-- 컬럼에 함수 적용
WHERE DATE(created_at) = '2026-04-27'

-- 앞 와일드카드 LIKE
WHERE name LIKE '%kim'

-- 타입 불일치
WHERE user_id = '123'  -- user_id가 숫자 컬럼이면 변환 비용 발생 가능

-- 부정 조건
WHERE status != 'CANCELLED'
```

대안:

```sql
-- 날짜 범위로 변경
WHERE created_at >= '2026-04-27 00:00:00'
  AND created_at <  '2026-04-28 00:00:00'
```

---

## 8. 실무 체크리스트

- 쿼리 빈도와 p95/p99 지연 시간을 먼저 확인한다.
- `WHERE`, `ORDER BY`, `LIMIT` 조합을 기준으로 인덱스를 설계한다.
- 복합 인덱스는 동등 조건 -> 범위 조건 -> 정렬 순서를 우선 고려한다.
- 대량 목록 조회는 OFFSET 대신 keyset pagination을 검토한다.
- 인덱스 추가 전후 `EXPLAIN`과 실제 실행 시간을 비교한다.
- 쓰기 많은 테이블에는 인덱스를 과도하게 추가하지 않는다.
- 카디널리티가 낮은 컬럼 단독 인덱스는 효과가 약할 수 있다.

### 면접 포인트

- Q: InnoDB에서 세컨더리 인덱스로 조회하면 왜 PK 조회가 한 번 더 발생하는가?
- Q: 복합 인덱스 `(a, b, c)`에서 `WHERE b = ?`가 비효율적인 이유는?
- Q: OFFSET 페이지네이션이 뒤 페이지에서 느려지는 이유는?
- Q: Covering Index의 장점과 단점은?
