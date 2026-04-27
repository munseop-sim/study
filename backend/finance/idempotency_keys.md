# 멱등성 키(Idempotency Key) — 금융 API 중복 처리 방지 설계

> **관련 문서**
> - [금액 정밀도 처리](./money_precision.md) — BigDecimal, 반올림 모드, 통화 연산
> - [SAGA & Outbox 패턴](../msa/saga_outbox_patterns.md) — 멱등성 소비자, 분산 트랜잭션

---

## 1. 멱등성(Idempotency)의 필요성

### 1.1 네트워크 타임아웃과 재시도의 위험

클라이언트가 서버에 요청을 보냈을 때 응답을 받지 못하는 경우는 크게 세 가지다.

```
[클라이언트]  ──요청──>  [서버]
              <── 응답 없음 (타임아웃)

원인 A: 요청이 서버에 도달하지 않음   → 서버 미처리
원인 B: 서버가 처리 중 장애           → 서버 일부 처리 또는 롤백
원인 C: 응답이 클라이언트에 도달 안 함 → 서버 처리 완료, 클라이언트 미인식
```

클라이언트 입장에서는 세 원인을 구분할 수 없다.
단순 재시도하면 원인 C의 경우 **동일한 요청을 두 번 처리**하게 된다.

### 1.2 금융 도메인의 치명적 결과

일반 API에서 중복 처리는 단순한 UX 오류에 그치지만, 금융 도메인에서는 치명적이다.

| 상황 | 중복 처리 결과 |
|---|---|
| 출금 API 중복 | 동일 금액 이중 출금 → 잔액 불일치 |
| 결제 API 중복 | 고객 이중 청구 → 민원, 환불 비용 발생 |
| 송금 API 중복 | 수취인에게 이중 입금 → 회수 불가 손실 |
| 포인트 지급 중복 | 부정 이득 → 회계 불일치 |

금전적 손실뿐 아니라 규제 기관 감사, 이용자 신뢰 손실로도 이어진다.

### 1.3 HTTP 메서드별 멱등성

| HTTP 메서드 | 자연 멱등 여부 | 이유 |
|---|---|---|
| `GET` | 자연 멱등 | 조회만 수행, 서버 상태 변경 없음 |
| `DELETE` | 자연 멱등 | 이미 삭제된 리소스를 다시 삭제해도 결과 동일 |
| `PUT` | 자연 멱등 | 동일 내용으로 덮어쓰기이므로 반복 가능 |
| `POST` | **비멱등** | 매번 새 리소스 생성 또는 트랜잭션 실행 |
| `PATCH` | 구현에 따라 다름 | replace처럼 같은 결과로 수렴하면 멱등 가능, increment처럼 누적 변경이면 비멱등 |

POST와 누적 변경형 PATCH는 설계를 통해 멱등성을 보장해야 한다. Idempotency Key가 바로 그 메커니즘이다.

---

## 2. Stripe Idempotency-Key 표준

Stripe가 제안하고 업계 표준으로 자리 잡은 방식이다.

### 2.1 요청/응답 흐름

```
[클라이언트]
  POST /v1/charges
  Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
  Body: { "amount": 5000, "currency": "KRW", ... }
          ↓
[서버]
  ┌ 이 키를 처음 보는가?
  │   YES → 비즈니스 로직 실행 → 응답 저장 → 응답 반환
  └   NO  → 저장된 응답 반환 (실제 처리 없음)
```

### 2.2 키 캐싱 규칙

| 항목 | 내용 |
|---|---|
| 캐시 범위 | 키 + 저장된 HTTP 상태코드 + 응답 본문 |
| 보존 기간 | **24시간** (Stripe 표준) |
| 만료 후 재사용 | 새 요청으로 처리 (재실행) |
| 키 생성 주체 | **클라이언트** |
| 키 형식 | UUID v4 권장, 최대 255자 |
| 키 고유성 | 요청당 유니크 (각 작업마다 새 키 생성) |

### 2.3 키 생성 원칙

```
// 올바른 사용: 작업마다 새로운 UUID 생성
String key = UUID.randomUUID().toString();  // 각 결제 시도마다

// 잘못된 사용: 고정 키 재사용
String key = "payment-key";  // 모든 결제에 동일 키 → 두 번째 결제부터 무시됨
```

키는 **하나의 비즈니스 의도**를 식별한다. 재시도 시에만 동일 키를 사용한다.

---

## 3. 스코프 설계: 글로벌 유니크 vs 리소스별 유니크

idempotency_key를 어떤 범위에서 유니크하게 관리할 것인지는 설계 핵심 결정 사항이다.

### 3.1 글로벌 유니크 (Stripe 표준)

```sql
UNIQUE (idempotency_key)
```

```
클라이언트 A: key="abc-123"  →  지갑 A 출금 처리
클라이언트 B: key="abc-123"  →  ? (글로벌 유니크라면 A의 응답 반환)
```

**장점**
- 구현 단순 (단일 컬럼 UK)
- 교차 리소스 중복 방지 (의도치 않은 재사용 원천 차단)

**단점**
- UUID 충돌 가능성 (통계적으로 극히 낮지만 이론상 존재)
- 테넌트(사용자)가 다른데 같은 키가 오면 충돌 → `user_id` 복합 UK 권장

### 3.2 리소스별 유니크

```sql
UNIQUE (wallet_id, idempotency_key)
```

```
지갑 A: key="abc-123"  →  정상 처리
지갑 B: key="abc-123"  →  별도 처리 (지갑이 다르므로 충돌 없음)
```

**장점**
- 지갑(리소스)마다 독립적인 키 공간 → 같은 키를 다른 지갑에 재사용 가능

**단점**
- 의도치 않게 동일 키로 다른 지갑에 요청이 통과될 수 있음
- 리소스 ID가 없는 생성(POST) 요청에 적용 불가

### 3.3 실무 권장: 사용자(클라이언트) 범위 복합 UK

```sql
UNIQUE (user_id, idempotency_key)
-- 또는 B2B 멀티테넌트 환경에서
UNIQUE (client_id, idempotency_key)
```

**이유**
- 사용자마다 독립적인 키 공간 제공
- 교차 사용자 간 키 충돌 방지 (보안)
- 생성 요청에도 적용 가능 (user_id는 인증 토큰에서 추출)
- Stripe 자체도 내부적으로 계정 단위 격리를 적용

---

## 4. Payload Mismatch 처리 (409 Conflict)

### 4.1 변조 공격 시나리오

```
1차 요청: key="abc-123", amount=10000  →  처리 완료, 응답 캐싱
2차 요청: key="abc-123", amount=99999  →  ???
```

Payload 검증 없이 키만 체크한다면, 2차 요청에서 캐싱된 1차 응답을 반환한다.
하지만 클라이언트가 의도적으로 다른 금액을 보낸 것인지 구분할 수 없다.
→ **동일 키로 amount 변조 시도를 탐지해야 한다.**

### 4.2 409 Conflict 반환 규칙

```
동일 키 + 동일 payload → 캐싱된 응답 반환 (정상 재시도)
동일 키 + 다른 payload → 409 Conflict 반환 (변조 또는 실수 탐지)
```

### 4.3 Payload Hash 비교 구현

요청 본문 전체를 저장하는 대신 핵심 필드의 해시만 비교한다.

```java
// 요청 핑거프린트 생성
String requestHash = sha256(
    userId + "|" + amount + "|" + currency + "|" + recipientAccountId
);

// 저장된 해시와 비교
if (!storedRecord.getRequestHash().equals(requestHash)) {
    throw new IdempotencyConflictException(
        "동일 키로 다른 요청이 감지되었습니다. 새 idempotency key를 사용하세요."
    );
}
```

**주의**: Stripe는 요청 파라미터의 핵심 필드만 비교하며, 무관한 헤더나 메타데이터는 제외한다. 비교 대상을 명확히 정의해야 한다.

### 4.4 409를 처리하지 않을 경우 위험

| 시나리오 | 미처리 시 결과 |
|---|---|
| 동일 키 + 다른 amount | 의도치 않게 캐싱된 응답 반환 → 클라이언트 오해 |
| 악의적 변조 | 작은 금액으로 대용량 처리를 우회할 수 있음 |
| 버그로 인한 payload 변경 | 잘못된 처리가 성공으로 응답 → 운영 장애 발견 지연 |

---

## 5. DB 구현 패턴

### 5.1 테이블 스키마

```sql
CREATE TABLE idempotency_records (
    id              BIGINT       PRIMARY KEY,
    idempotency_key VARCHAR(64)  NOT NULL,
    user_id         BIGINT       NOT NULL,
    request_hash    VARCHAR(64)  NOT NULL,       -- SHA-256 핵심 필드 해시
    status          VARCHAR(20)  NOT NULL,       -- PROCESSING / COMPLETED / FAILED
    response_status INT,                         -- HTTP 상태 코드 (COMPLETED 이후 저장)
    response_body   TEXT,                        -- 직렬화된 응답 JSON
    locked_until    TIMESTAMP,                   -- PROCESSING 고아 레코드 복구 기준
    attempt_count   INT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP    NOT NULL,
    expires_at      TIMESTAMP    NOT NULL,       -- 만료 시각 (created_at + 24h)
    UNIQUE (user_id, idempotency_key)
);

CREATE INDEX idx_idempotency_expires_at ON idempotency_records (expires_at);
```

### 5.2 권장 처리 흐름: PROCESSING → COMPLETED

```
[요청 수신]
    ↓
1. INSERT status=PROCESSING 선점
   INSERT INTO idempotency_records (...)
   VALUES (..., 'PROCESSING', locked_until = now() + interval '30 seconds', ...)

2. INSERT 성공?
   ├── YES → 이 요청이 처리 권한을 획득
   │       → 비즈니스 로직 실행
   │       → 결과를 response_status, response_body와 함께 COMPLETED로 UPDATE
   └── NO  → UNIQUE(user_id, idempotency_key) 충돌
           → 기존 레코드 SELECT
               ├── request_hash 다름 → 409 Conflict
               ├── status=COMPLETED → 저장된 응답 반환
               ├── status=FAILED → 실패 응답 반환 또는 재시도 정책 적용
               ├── status=PROCESSING, locked_until 미만 → 409/202 + Retry-After
               └── status=PROCESSING, locked_until 초과 → 복구 정책에 따라 재시도 또는 실패 처리
```

핵심은 **비즈니스 로직 실행 전에 멱등성 키를 선점**하는 것이다.
`UNIQUE (user_id, idempotency_key)`가 같은 키의 동시 처리 권한을 하나의 요청으로 제한한다.

```sql
-- 1. 처리 권한 선점
INSERT INTO idempotency_records (
    id,
    user_id,
    idempotency_key,
    request_hash,
    status,
    locked_until,
    attempt_count,
    created_at,
    updated_at,
    expires_at
) VALUES (
    ?, ?, ?, ?, 'PROCESSING',
    NOW() + INTERVAL '30 seconds',
    1,
    NOW(),
    NOW(),
    NOW() + INTERVAL '24 hours'
);
```

```sql
-- 2. 처리 완료 후 결과 저장
UPDATE idempotency_records
SET status = 'COMPLETED',
    response_status = ?,
    response_body = ?,
    updated_at = NOW()
WHERE user_id = ?
  AND idempotency_key = ?
  AND status = 'PROCESSING';
```

### 5.3 중복 요청 처리 정책

이미 `PROCESSING`인 키로 같은 요청이 들어오면 선택지는 세 가지다.

| 정책 | 응답 | 적합한 상황 |
|---|---|---|
| 즉시 충돌 | `409 Conflict` | 클라이언트가 재시도를 제어할 수 있을 때 |
| 처리 중 응답 | `202 Accepted` + `Retry-After` | 비동기 처리 API |
| 짧은 대기 후 재조회 | 서버에서 100~500ms 대기 | 처리 시간이 매우 짧고 UX가 중요한 API |

금융 API에서는 무한 대기보다 명확한 재시도 응답이 안전하다.
클라이언트는 `Retry-After` 이후 같은 idempotency key로 다시 요청하고, 서버는 `COMPLETED` 상태가 되면 저장된 응답을 반환한다.

### 5.4 PROCESSING 고아 레코드 복구

`PROCESSING`을 먼저 저장하면 서버가 처리 도중 죽는 경우가 생긴다.
이를 위해 `locked_until`, `attempt_count`, 상태 전이 정책이 필요하다.

```
1. status=PROCESSING, locked_until 미만
   → 아직 처리 중으로 간주

2. status=PROCESSING, locked_until 초과
   → 이전 처리자가 죽었거나 응답 저장에 실패한 것으로 판단
   → 외부 side-effect 발생 여부 확인 후 재처리/실패 처리

3. attempt_count 초과
   → FAILED로 마킹하고 운영자 확인 또는 DLQ 성격의 보정 큐로 이동
```

주의: PG 승인, 송금 요청처럼 외부 side-effect가 있을 수 있는 작업은 `locked_until`이 지났다고 무조건 재실행하면 안 된다.
외부 거래 ID 또는 원장 상태를 먼저 조회해 이미 처리됐는지 확인해야 한다.

### 5.5 위험한 패턴: SELECT 후 비즈니스 로직 후 INSERT

다음 흐름은 결과 캐시는 하나만 남길 수 있지만, 비즈니스 로직 중복 실행을 막지 못한다.

```
Thread A: SELECT → 없음 → 비즈니스 로직 실행 → INSERT 시도
Thread B: SELECT → 없음 → 비즈니스 로직 실행 → INSERT 시도
→ Thread A INSERT 성공
→ Thread B INSERT → UK violation!
```

Thread B가 UK violation을 받으면 **재조회 후 Thread A의 결과를 반환**한다.
단, 비즈니스 로직이 두 번 실행되는 문제가 있다.
출금, 결제 승인, 외부 PG 호출처럼 side-effect가 있는 작업에서는 이 패턴을 피해야 한다.

### 5.6 분산 락을 함께 쓰는 경우

```java
// Redis 분산 락을 활용한 같은 키 동시 진입 직렬화
String lockKey = "idempotency:" + userId + ":" + idempotencyKey;
boolean locked = redisLock.tryLock(lockKey, 10, TimeUnit.SECONDS);
if (!locked) {
    throw new IdempotencyLockException("동시 요청 처리 중입니다. 잠시 후 재시도하세요.");
}
try {
    // INSERT PROCESSING → 비즈니스 로직 → UPDATE COMPLETED 수행
} finally {
    redisLock.unlock(lockKey);
}
```

분산 락은 UX와 부하를 개선하는 보조 수단이다.
최종 방어선은 여전히 DB의 `UNIQUE (user_id, idempotency_key)` 제약이어야 한다.

---

## 6. 보존 기간 및 만료 처리

### 6.1 만료 정책

| 항목 | 내용 |
|---|---|
| Stripe 표준 | **24시간** |
| 금융 규제 환경 | 감사 요건에 따라 더 길게 보존할 수도 있음 |
| 만료 후 재사용 | 새 요청으로 처리 (재실행 발생 주의) |

만료 후 같은 키로 재시도하면 새로운 처리가 시작된다.
따라서 클라이언트는 24시간 이내에만 재시도해야 한다.

### 6.2 만료 레코드 정리 방법

#### 배치 Cleanup

```sql
-- 만료된 레코드 주기적 삭제 (스케줄러)
DELETE FROM idempotency_records
WHERE expires_at < NOW()
LIMIT 1000;  -- 한 번에 대량 삭제 방지
```

#### TTL 인덱스 활용 (PostgreSQL pg_partman 또는 Range Partition)

대용량 환경에서는 파티셔닝으로 만료 데이터를 파티션 DROP으로 빠르게 제거한다.

```sql
-- created_at 기준 월별 파티션
CREATE TABLE idempotency_records (...)
PARTITION BY RANGE (created_at);

CREATE TABLE idempotency_records_2025_04
PARTITION OF idempotency_records
FOR VALUES FROM ('2025-04-01') TO ('2025-05-01');
-- 30일 후 파티션 전체 DROP → O(1) 삭제
```

---

## 7. 요약 비교

| 항목 | 글로벌 UK | 사용자 범위 UK | 리소스 범위 UK |
|---|---|---|---|
| 스코프 | `(idempotency_key)` | `(user_id, idempotency_key)` | `(resource_id, idempotency_key)` |
| 교차 사용자 충돌 방지 | O | O | X |
| 키 공간 | 전역 공유 | 사용자별 독립 | 리소스별 독립 |
| 생성 요청 적용 | 가능 | 가능 | 어려움 |
| 실무 권장 | 단순 단일 테넌트 | **멀티테넌트 표준** | 특수 케이스 |

---

## 면접 포인트

### Q1. 글로벌 유니크 UK vs 리소스별 UK 중 어떤 것을 선택하겠는가?

`(user_id, idempotency_key)` 복합 UK를 선택한다.
글로벌 유니크는 구현이 단순하지만, 서로 다른 사용자가 같은 UUID를 생성하면(극히 드물지만) 충돌이 발생한다.
리소스별 UK는 동일 키를 다른 리소스에 재사용할 수 있어 의도치 않은 통과가 생길 수 있다.
`(user_id, idempotency_key)` 복합 UK는 멀티테넌트 환경에서 사용자마다 독립적인 키 공간을 제공하면서, 교차 사용자 간 키 충돌을 원천 방지한다.
생성(POST) 요청에도 적용 가능하며 인증 토큰에서 user_id를 자동 추출할 수 있어 클라이언트 부담도 없다.

### Q2. 동일 idempotency_key로 amount가 다른 요청이 오면 어떻게 처리해야 하는가?

**409 Conflict**를 반환해야 한다.
동일 키는 동일한 비즈니스 의도를 나타내야 한다. amount가 다르다는 것은 다른 의도이거나 변조 시도다.
구현은 `PROCESSING` 레코드를 선점 INSERT할 때 요청 핵심 필드(amount, currency, recipientId 등)의 SHA-256 해시를 함께 저장하고, 재요청 시 현재 요청의 해시와 비교한다.
불일치 시 409와 함께 "새 idempotency key를 사용하라"는 안내를 반환한다.
이를 처리하지 않으면 악의적 클라이언트가 작은 금액 처리의 응답 캐시를 이용해 대금 결제를 우회하는 보안 취약점이 생길 수 있다.

### Q3. 멱등성 체크와 트랜잭션의 관계 — 어느 격리 수준에서 안전한가?

멱등성 보장의 최종 방어선은 격리 수준이 아니라 **`UNIQUE (user_id, idempotency_key)` 제약**이다.
격리 수준을 Read Committed 이상으로 설정해도 두 트랜잭션이 동시에 SELECT에서 "없음"을 보고 비즈니스 로직을 실행하는 문제는 해결되지 않는다.

권장 흐름은 비즈니스 로직 전에 `status=PROCESSING` 레코드를 먼저 INSERT해서 처리 권한을 선점하는 것이다.
INSERT에 성공한 요청만 비즈니스 로직을 실행하고, 나머지 동시 요청은 UK violation을 받은 뒤 기존 레코드를 재조회한다.

Serializable 격리 수준은 일부 경쟁을 serialization failure로 바꿀 수 있지만 비용이 크고 재시도 설계가 필요하다.
실무에서는 `UK 제약 + PROCESSING 선점 INSERT`를 기본으로 두고, 같은 키의 동시 요청 부하를 줄이기 위해 Redis 분산 락을 보조적으로 사용할 수 있다.

### Q4. idempotency key를 DB UK로 처리할 때 동시 요청 경쟁 조건은 어떻게 해결하는가?

동시에 같은 키로 두 요청이 오면 둘 다 SELECT부터 시작하지 않는다.
먼저 `INSERT status=PROCESSING`을 시도한다.

```
Thread A: INSERT PROCESSING 성공 → 비즈니스 로직 실행 → UPDATE COMPLETED
Thread B: INSERT PROCESSING 실패(UK violation) → 기존 레코드 조회
```

Thread B는 기존 레코드의 상태에 따라 처리한다.
- `COMPLETED`면 저장된 응답 반환
- `FAILED`면 저장된 실패 응답을 반환하거나 정책에 따라 새 시도를 허용
- `PROCESSING`이면 `409 Conflict` 또는 `202 Accepted + Retry-After` 반환
- `PROCESSING`이지만 `locked_until`이 지났으면 복구 정책 수행

Redis 분산 락은 같은 키의 동시 진입을 줄이는 보조 수단으로 사용할 수 있지만, DB UK 제약을 대체해서는 안 된다.

### Q5. 멱등성 레코드를 언제 저장해야 하는가 — 비즈니스 로직 전 vs 후?

비즈니스 로직 전에 `PROCESSING` 레코드를 먼저 저장해 처리 권한을 선점하고, 비즈니스 로직 완료 후 결과를 `COMPLETED`로 업데이트한다.

```
INSERT PROCESSING
→ 비즈니스 로직 실행
→ UPDATE COMPLETED(response_status, response_body)
```

단순히 비즈니스 로직 완료 후에만 INSERT하면 동시 요청이 모두 비즈니스 로직에 진입할 수 있다.
반대로 `PROCESSING`만 저장하고 복구 정책이 없으면 서버 장애 시 고아 레코드가 남는다.

따라서 실무 구현에는 다음이 필요하다.
- `status`: PROCESSING / COMPLETED / FAILED
- `locked_until`: 처리자 장애 감지 기준
- `attempt_count`: 무한 재처리 방지
- 외부 거래 ID 또는 원장 조회: `PROCESSING` 만료 후 재실행 전 side-effect 확인
