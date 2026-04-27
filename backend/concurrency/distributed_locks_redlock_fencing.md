# 분산 락 — Redis SETNX, Redlock, Fencing Token 설계

## 1. Redis SETNX 기반 단일 노드 분산 락

### 핵심 개념

단일 Redis 노드를 이용해 분산 환경에서 임계 영역을 보호하는 가장 간단한 방법이다.
`SETNX`(SET if Not eXists)에 만료 시간을 결합하여 락을 구현한다.

### SET NX PX 명령

Redis 2.6.12 이후부터는 `SET key value NX PX {ttl}` 단일 명령으로 원자적으로 락을 획득할 수 있다.

```
SET lock:transfer:userId-42 "owner-uuid-1234" NX PX 5000
```

- `NX`: 키가 존재하지 않을 때만 SET (락 획득)
- `PX 5000`: TTL 5000ms — 프로세스 사망 시 락이 자동 해제되도록 보장

```java
// Java + Lettuce/Jedis 예시
public boolean tryAcquireLock(String lockKey, String lockValue, long ttlMillis) {
    // SET lock:key lockValue NX PX ttlMillis
    String result = jedis.set(lockKey, lockValue, SetParams.setParams().nx().px(ttlMillis));
    return "OK".equals(result);
}
```

`lockValue`에는 소유자를 식별할 수 있는 고유 값(UUID + 스레드 ID 등)을 사용해야 한다.
그래야 락 해제 시 자신이 획득한 락만 해제할 수 있다.

### 해제 시 Lua Script (GET+DEL 원자성)

단순히 `DEL lockKey`를 호출하면 다음 위험이 있다.

```
1. 프로세스 A가 GET lock:key → "owner-uuid-1234" (A의 소유)
2. TTL 만료 → 락 자동 해제
3. 프로세스 B가 락 획득
4. 프로세스 A가 DEL lock:key → B의 락을 해제!
```

이를 방지하기 위해 GET과 DEL을 원자적으로 수행하는 Lua script를 사용한다.

```lua
-- 원자적 락 해제 Lua Script
if redis.call("GET", KEYS[1]) == ARGV[1] then
    return redis.call("DEL", KEYS[1])
else
    return 0
end
```

```java
// Lua script 실행
private static final String UNLOCK_SCRIPT =
    "if redis.call('GET', KEYS[1]) == ARGV[1] then " +
    "    return redis.call('DEL', KEYS[1]) " +
    "else " +
    "    return 0 " +
    "end";

public void releaseLock(String lockKey, String lockValue) {
    jedis.eval(UNLOCK_SCRIPT,
        Collections.singletonList(lockKey),
        Collections.singletonList(lockValue));
}
```

### 단일 노드 락의 한계

| 상황 | 결과 |
|---|---|
| Redis 노드 장애 | 락 정보 유실 → 새 요청이 락 없이 진입 가능 |
| AOF fsync=always 미설정 | 재시작 후 락 유실 |
| Master–Replica 구성 | Master 쓰기 후 Replica 복제 전 Failover 시 두 클라이언트가 동시에 락 획득 가능 |

---

## 2. Redlock 알고리즘 (독립 Redis 노드 쿼럼)

### 핵심 개념

Redlock은 **독립된 Redis 노드 N개에 과반수 쿼럼(majority quorum)** 으로 락을 획득하여 단일 노드 장애 문제를 해결하려는 알고리즘이다.
Redis 공식 문서에 소개된 알고리즘이며, Martin Kleppmann은 Redlock의 안전성 한계를 비판한 대표적인 인물이다.

Redlock 구성은 장애 허용성과 쿼럼 효율 때문에 보통 홀수 개(`2f + 1`)의 독립 Redis master를 사용한다.
예를 들어 5개 노드는 2개 장애까지 허용하고, 3개 이상에서 락을 획득해야 성공으로 판단한다.
짝수 개도 과반수 계산은 가능하지만 4개 노드는 1개 장애까지만 허용하여 3개 구성과 장애 허용성이 같으므로 효율이 낮다.

### 과반수(3/5) 획득 조건

```
노드: R1  R2  R3  R4  R5
락 획득: OK  OK  OK  FAIL FAIL
                 ↑
          3개 성공 (과반수) → 락 획득 성공
```

알고리즘 절차:

```
1. 현재 시각 T1 기록
2. N개 노드 각각에 SET lockKey uniqueValue NX PX {TTL} 시도
   (각 노드별 응답 대기 시간은 TTL의 극히 일부, 예: 50ms)
3. 성공 노드 수 >= quorum(floor(N/2) + 1) 이고
   경과 시간 elapsed = T2 - T1 < TTL 이면 → 락 획득 성공
4. 실패 시 획득한 모든 노드에서 즉시 해제
```

### 유효 시간 계산

락 획득에 성공했더라도 이미 일부 시간이 소요되었으므로 실제 유효 시간은 다음과 같다.

```
validityTime = TTL - elapsed - clockDrift
```

- `elapsed`: T1~T2 사이 소요 시간
- `clockDrift`: 노드 간 시계 오차 보정값 (예: `TTL * 0.01 + 2ms`)

`validityTime`이 음수이거나 너무 짧으면 락 사용을 포기하고 즉시 모든 노드에서 해제한다.

```java
// 유효 시간 계산 예시 (개념)
long elapsed = System.currentTimeMillis() - startTime;
long clockDrift = (long)(ttl * 0.01) + 2;
long validityTime = ttl - elapsed - clockDrift;

if (acquiredCount >= quorum && validityTime > 0) {
    // 락 획득 성공, validityTime 내에 작업 완료해야 함
} else {
    // 실패: 획득한 노드 전부 해제
    releaseOnAllNodes(lockKey, uniqueValue);
}
```

---

## 3. Kleppmann의 Redlock 비판 — GC pause / clock drift

### 핵심 개념

Martin Kleppmann은 2016년 블로그 포스트 "How to do distributed locking"에서 Redlock이 **safety를 보장하지 못한다**고 주장했다.
두 가지 주요 비판이 있다.

### 3.1 GC Stop-the-World 시나리오

```
1. 프로세스 A: Redlock 획득 성공 (TTL = 10s)
2. 프로세스 A: JVM Full GC 발생 → 15초 STW(Stop-The-World)
3. TTL 10s 경과 → 락 자동 만료
4. 프로세스 B: Redlock 획득 성공
5. 프로세스 B: 공유 자원(DB, 파일 등)에 쓰기 시작
6. 프로세스 A: GC 복귀 → 아직 락을 보유 중이라고 "착각"
7. 프로세스 A: 공유 자원에 쓰기 시작

→ A와 B가 동시에 같은 자원을 수정 (데이터 손상!)
```

TTL은 벽시계(wall clock) 시간에 의존하지만, GC pause는 프로세스 입장에서 시간이 멈추므로 TTL 만료를 인지할 수 없다.

### 3.2 NTP Clock Jump 시나리오

```
노드 R3의 NTP 동기화로 시계가 10분 앞으로 점프
→ R3에 저장된 락의 TTL이 즉시 만료된 것으로 처리
→ 기존 락 보유자 A는 아직 락이 유효하다고 착각
→ 클라이언트 B가 만료된 노드를 포함해 새 쿼럼을 획득
→ A와 B가 동시에 같은 자원에 접근할 수 있음
```

Redlock은 서로 다른 노드의 시계가 "대략 동기화"되어 있다고 가정하지만, 실제로 NTP 슬루잉(slewing)이나 PTP 재조정으로 갑작스러운 시계 점프가 발생할 수 있다.

### 결론

Kleppmann의 주장: **"Redlock은 안전하지 않다 — 사용하지 말라"**

- Redlock은 `timing assumption`(시간 가정)에 의존하는데, 비동기 네트워크와 JVM 환경에서 이 가정은 깨질 수 있다.
- 엄격한 상호 배제(mutual exclusion)가 필요한 경우 Redlock은 부적절하다.
- Antirez(Redis 창시자)는 반론을 제기했지만, 금융처럼 정확성이 중요한 도메인에서는 Kleppmann의 우려를 무시할 수 없다.

---

## 4. Fencing Token (펜싱 토큰)

### 핵심 개념

Fencing Token은 **GC pause나 clock drift 문제를 저장소 레벨에서 해결**하는 방법이다.
락을 획득할 때 단조 증가하는 토큰(monotonically increasing token)을 함께 발급받고, 저장소가 이 토큰으로 요청의 유효성을 검증한다.

### 동작 원리

```
1. 락 서버: 클라이언트 A에게 락 발급 (token = 33)
2. 락 서버: 클라이언트 B에게 락 발급 (token = 34, A의 락 만료 후)
3. B: storage.write(data, fencingToken=34) → 성공
4. A: GC 복귀 후 storage.write(data, fencingToken=33) 시도
   → Storage: "현재 최신 token=34 > 요청 token=33 → 거부!"
```

```java
// Storage 측 검증 로직 (개념)
public void writeData(Data data, long fencingToken) {
    long currentToken = tokenStore.getCurrentToken(data.getResourceId());
    if (fencingToken < currentToken) {
        throw new StaleTokenException(
            "요청 token=" + fencingToken + "은 이미 만료됨. currentToken=" + currentToken
        );
    }
    tokenStore.updateToken(data.getResourceId(), fencingToken);
    dataStore.write(data);
}
```

### Redis 기반 구현이 어려운 이유

Redis로 Fencing Token을 구현하기 위해서는 다음이 모두 필요하다.

1. 락 발급마다 단조 증가하는 시퀀스 번호를 생성
2. Storage(DB)가 요청마다 토큰을 검증하고 거부 로직 실행
3. Redis 자체의 단조성 보장 — Redis `INCR`는 단일 노드에서는 원자적이지만, Redlock(다중 노드)에서는 각 노드의 카운터가 독립적으로 관리되어 전역 단조성을 보장하기 어렵다.

특히 **Redis는 락 발급 시 단조 증가 토큰을 자연스럽게 생성하는 메커니즘이 없다.**
Redlock이 여러 노드에 분산되어 있으므로, 어떤 노드를 "토큰 발행 권한자"로 삼을지 결정하는 순간 다시 단일 노드 의존성 문제로 돌아간다.

### ZooKeeper ephemeral sequential node

ZooKeeper는 Fencing Token 구현에 자연스럽게 적합하다.

```
/locks/transfer (ephemeral sequential)
  ├── lock-0000000033  ← 클라이언트 A가 획득
  ├── lock-0000000034  ← 클라이언트 B 대기 중
```

- `lock-0000000033`의 suffix sequence number가 락 획득 순서를 나타내며 Fencing Token으로 활용될 수 있다.
- `zxid`는 ZooKeeper 내부 트랜잭션 ID이므로, 클라이언트가 락 Fencing Token으로 직접 사용하는 값이 아니다.
- 클라이언트가 연결을 끊으면 ephemeral 노드가 자동 삭제 → 락 자동 해제
- Apache Curator의 lock recipe도 ephemeral sequential node의 순서를 기반으로 락 소유자를 결정한다.

주의: ZooKeeper가 sequence number를 제공한다고 해서 Fencing이 자동 완성되는 것은 아니다.
클라이언트는 획득한 sequence number를 실제 저장소 쓰기 요청에 함께 전달해야 하고, 저장소는 최신 token보다 작은 요청을 거부해야 한다.

---

## 5. 실무 대안 비교

### 5.1 Redisson (tryLock + Watchdog)

Java 환경에서 가장 많이 쓰이는 Redis 분산 락 클라이언트다.

```java
RLock lock = redissonClient.getLock("lock:transfer:" + userId);
boolean acquired = lock.tryLock(waitTime, leaseTime, TimeUnit.MILLISECONDS);
if (acquired) {
    try {
        // 임계 영역
        processTransfer(userId, amount);
    } finally {
        lock.unlock();
    }
}
```

**Watchdog 메커니즘**: 기본 TTL(30s)을 작업이 완료될 때까지 자동으로 연장한다.
`leaseTime = -1`로 설정하면 watchdog이 활성화되어 10초마다 TTL을 30초로 갱신한다.

```
[Watchdog Timer (10s마다)]
    → PEXPIRE lock:key 30000  # TTL 자동 갱신
```

**GC pause 위험 잔존**: Watchdog이 GC STW 중에 실행되지 못하면 갱신이 지연되고, TTL이 만료되어 다른 클라이언트가 락을 획득할 수 있다.

### 5.2 ZooKeeper 기반 분산 락 (Apache Curator)

```java
InterProcessMutex mutex = new InterProcessMutex(client, "/locks/transfer/" + userId);
if (mutex.acquire(30, TimeUnit.SECONDS)) {
    try {
        processTransfer(userId, amount);
    } finally {
        mutex.release();
    }
}
```

| 항목 | 내용 |
|---|---|
| CP 보장 | 과반수 ZooKeeper 노드 장애 시에만 서비스 중단 |
| Fencing 가능 | ephemeral sequential node의 sequence number를 Fencing Token으로 활용 가능 |
| 세션 만료 | 클라이언트 연결 끊김 시 ephemeral 노드 자동 삭제 |
| 주의점 | lock 획득만으로 Fencing이 끝나지 않음. sequence token을 저장소에 전달하고 검증해야 함 |
| 운영 복잡도 | ZooKeeper 클러스터 운영, 홀수 노드 구성 필요 |

### 5.3 etcd (Lease + Revision)

쿠버네티스 내부에서 사용하는 CP 저장소. `Revision`이 Fencing Token 역할을 한다.

```
etcd: PUT /locks/transfer/{userId}  (Lease ID=abc, TTL=10s)
      → ModRevision = 42 반환 (단조 증가, 전역 유일)
```

- Lease TTL 만료 시 키 자동 삭제
- `ModRevision`을 Fencing Token으로 활용 가능
- k8s 환경에서는 etcd가 이미 인프라에 존재하므로 추가 구축 비용 낮음

### 5.4 DB 기반 비관적 락

```sql
-- 락 테이블 방식
INSERT INTO distributed_lock (resource_id, owner, expired_at)
VALUES (?, ?, NOW() + INTERVAL '10 seconds')
ON CONFLICT (resource_id) DO NOTHING;
-- 영향받은 행 = 1 → 락 획득 성공, 0 → 실패

-- 또는 SELECT FOR UPDATE 방식 (DB 행 자체를 락으로)
SELECT * FROM resource_lock WHERE resource_id = ? FOR UPDATE;
```

| 항목 | 내용 |
|---|---|
| 구현 단순성 | 높음 (별도 인프라 없음) |
| DB 부하 | 락 빈도가 높으면 부하 집중 |
| Fencing 가능 | 기본 없음. `version`, `fencing_token`, 조건부 UPDATE와 조합해야 가능 |
| GC pause 대응 | 트랜잭션이 살아 있는 동안 다른 writer를 블로킹하지만, 구 보유자 쓰기를 토큰으로 거부하는 구조는 아님 |
| 적합 규모 | 소~중규모, 락 빈도가 낮은 경우 |

### 5.5 실무 대안 요약

| 솔루션 | CAP | Fencing Token | GC pause 대응 | 운영 복잡도 | 적합 사례 |
|---|---|---|---|---|---|
| Redis SETNX | AP | 불가 | TTL 만료 후 stale writer 가능 | 낮음 | 단기 중복 방지 |
| Redisson Watchdog | AP | 불가 | GC STW 중 watchdog도 멈춤 | 낮음 | 일반 비즈니스 락 |
| ZooKeeper (Curator) | CP | 가능 | sequence token으로 stale write 거부 가능 | 높음 | 강한 정합성 필요 |
| etcd Lease | CP | 가능 | revision으로 stale write 거부 가능 | 중간 | k8s 환경 |
| DB 비관적 락 | CP | 기본 없음 | 트랜잭션 락으로 다른 writer 블로킹 | 낮음 | 간단한 단일 DB |
| DB 비관적 락 + version/token | CP | 가능 | 조건부 UPDATE로 stale write 거부 가능 | 중간 | 단일 DB에서 강한 정합성 |

---

## 6. 금융 도메인 선택 기준

### 핵심 개념

금융 서비스에서 분산 락은 단순한 성능 도구가 아니라 **데이터 정합성의 최후 방어선**이다.
솔루션 선택 시 "빠른 락"보다 "정확한 락"이 우선이다.

### 6.1 "정확히 한 번" vs "중복 방지"

잔액 변경, 이체, 출금처럼 **"정확히 한 번(exactly once)"** 실행이 보장되어야 하는 작업은 Fencing Token 지원이 필수다.

```
위험한 시나리오:
1. 프로세스 A: 잔액 100원 확인 → 50원 출금 결정
2. GC pause 20s → TTL 만료
3. 프로세스 B: 같은 계좌에서 락 획득 → 80원 출금
4. 프로세스 A: GC 복귀 → 50원 출금 수행
→ 잔액 = 100 - 80 - 50 = -30원 (오버드래프트!)
```

이 경우 Fencing Token이 없는 Redis SETNX는 부적절하다.

### 6.2 Redis SETNX의 적합한 사용처

Redis 분산 락은 **멱등 체크 보조 수단**으로는 유효하다.

```java
// 동일 요청 ID의 중복 처리 방지 (짧은 TTL, 결과에 치명적이지 않은 경우)
String idempotencyKey = "req:" + requestId;
if (!tryAcquireLock(idempotencyKey, "processing", 5000)) {
    throw new DuplicateRequestException("이미 처리 중인 요청");
}
```

단, 이 방식은 "완전히 한 번만 처리"를 보장하지 않으므로, 반드시 **DB 트랜잭션 레벨의 멱등 체크**와 병행해야 한다.

### 6.3 멱등 체크 + DB 비관적 락 조합

실무에서 가장 검증된 패턴은 DB 자체를 진실의 원천(source of truth)으로 사용하는 것이다.

```java
@Transactional
public WithdrawResult withdraw(String requestId, Long accountId, BigDecimal amount) {
    // 1. 멱등 체크 (같은 트랜잭션)
    if (withdrawRequestRepository.existsByRequestId(requestId)) {
        return WithdrawResult.ALREADY_PROCESSED;
    }

    // 2. DB 비관적 락 (SELECT FOR UPDATE)
    Account account = accountRepository.findByIdForUpdate(accountId)
        .orElseThrow(AccountNotFoundException::new);

    // 3. 잔액 검증 및 차감
    account.withdraw(amount);
    accountRepository.save(account);

    // 4. 요청 기록 (멱등 체크용)
    withdrawRequestRepository.save(new WithdrawRequest(requestId, accountId, amount));

    return WithdrawResult.SUCCESS;
}
```

이 패턴은 다음을 보장한다.
- `SELECT FOR UPDATE`가 동시 출금을 순차 처리
- `requestId` 유니크 제약으로 같은 요청의 이중 처리 방지
- 트랜잭션 범위 내에서 모든 검증·쓰기가 원자적으로 수행

### 6.4 선택 기준 요약

```
질문 1: 작업이 "정확히 한 번" 보장이 필요한가?
  YES → Fencing Token 지원 필요
       → ZooKeeper, etcd, DB 비관적 락 + version/token 검토
  NO  → Redis SETNX / Redisson으로 충분할 수 있음 (멱등 체크 병행 필수)

질문 2: 이미 DB 트랜잭션으로 정합성을 보장하는가?
  YES → DB 비관적 락(SELECT FOR UPDATE) + 멱등 테이블이 가장 단순하고 안전
  NO  → 분산 락 솔루션 별도 도입 검토

질문 3: 처리량이 매우 높고 DB가 병목인가?
  YES → Redis + 엄격한 멱등 체크 조합, 또는 etcd 검토
  NO  → DB 비관적 락으로 충분
```

---

## 7. 면접 Q&A

### Q1: SETNX 분산 락에서 프로세스가 TTL 내에 작업을 못 마치면 어떻게 되나요?

**A:**
TTL이 만료되면 락 키가 자동 삭제되고 다른 프로세스가 락을 획득할 수 있다.
이 경우 원래 프로세스는 여전히 작업 중이므로 **두 프로세스가 동시에 임계 영역에 진입**하게 된다.

Redisson의 Watchdog은 이 문제를 TTL 자동 갱신으로 완화하지만, GC STW가 발생하면 Watchdog 스레드도 멈추므로 완전한 해결책이 아니다.
근본적인 해결책은 **Fencing Token**으로, 저장소가 만료된 토큰의 쓰기를 거부한다.

실무에서는 TTL을 작업 예상 시간의 2~3배로 넉넉히 잡고, 동시에 DB 트랜잭션으로 정합성을 이중으로 보장하는 것이 안전하다.

---

### Q2: Redlock이 Kleppmann에게 비판받는 구체적 시나리오를 설명해 주세요.

**A:**
두 가지 시나리오가 있다.

**GC pause 시나리오:**
프로세스 A가 Redlock을 획득(TTL=10s)한 직후 JVM Full GC로 15초간 STW가 발생한다.
이 사이 TTL이 만료되고 프로세스 B가 락을 획득하여 데이터를 수정하기 시작한다.
A는 GC 복귀 후 자신이 여전히 락을 보유 중이라고 착각하고 같은 데이터를 수정한다.

**NTP clock jump 시나리오:**
5개 Redis 노드 중 R3의 NTP 동기화로 시계가 앞으로 점프한다.
일부 노드의 락 TTL이 예상보다 빨리 만료되면, 기존 보유자 A가 락이 유효하다고 착각하는 동안 다른 프로세스 B가 새 쿼럼을 획득할 수 있다.
문제는 쿼럼 수가 부족해지는 것이 아니라, 시간 가정이 깨지면서 두 클라이언트가 각각 자신을 유효한 락 보유자로 믿을 수 있다는 점이다.

Kleppmann의 핵심 주장은 **"비동기 네트워크와 JVM 환경에서 시간 가정(timing assumption)에 의존하는 Redlock은 안전하지 않다"** 는 것이다.

---

### Q3: Fencing Token이 Redis로 구현하기 어려운 이유는 무엇인가요?

**A:**
Fencing Token의 핵심 조건은 **락 발급마다 전역 단조 증가하는 숫자** 를 보장하는 것이다.

단일 Redis 노드라면 `INCR` 명령으로 단조 증가 시퀀스를 만들 수 있다.
하지만 Redlock은 5개 독립 Redis 노드에 분산되어 있고, 각 노드는 독립적인 카운터를 가진다.
따라서 "어느 노드의 카운터를 Fencing Token으로 사용할지" 를 결정하는 순간 단일 노드 의존성 문제로 돌아간다.

또한 Redis는 저장소(DB) 역할을 하지 않으므로, **"Storage가 토큰을 검증하고 구 토큰을 거부하는 로직"** 을 Redis 자체가 제공하지 않는다.
결국 Fencing Token의 검증 주체는 Redis가 아니라 실제 데이터를 보관하는 저장소(DB 등)여야 한다.

ZooKeeper는 ephemeral sequential node의 sequence number를 통해 자연스럽게 Fencing Token을 제공한다.
`zxid`는 ZooKeeper 내부 트랜잭션 ID라서 클라이언트 락 토큰으로 직접 쓰는 값이 아니다.
단, 이 sequence number도 실제 데이터 저장소에 전달되어 검증될 때만 stale write를 막을 수 있다.

---

### Q4: 금융 서비스에서 분산 락을 어떻게 선택하겠는가?

**A:**
먼저 "이미 DB 트랜잭션으로 정합성을 보장하고 있는가"를 확인한다.

**DB 트랜잭션으로 충분한 경우** (단일 DB, 단순 출금/잔액 변경):
`SELECT FOR UPDATE` + 멱등 요청 테이블 조합이 가장 안전하다.
추가 인프라 없이 ACID 보장이 되고, 트랜잭션 락이 동시 writer를 블로킹한다.
다만 이것은 Fencing Token과 동일한 메커니즘은 아니며, stale write 거부가 필요하면 `version` 또는 `fencing_token` 조건부 UPDATE를 함께 설계해야 한다.

**여러 서비스에 걸친 경우** (MSA, 분산 트랜잭션):
Redis SETNX는 단기 중복 방지(5s 이하 TTL) 보조 수단으로만 쓰고,
실제 정합성은 SAGA + Outbox + 멱등 체크로 보장한다.

**엄격한 "정확히 한 번" 보장이 필요하고 처리량도 높은 경우:**
etcd Lease(k8s 환경) 또는 ZooKeeper(Curator)를 검토한다.
둘 다 CP이고 Fencing Token을 제공하여 GC pause 문제도 Storage 레벨에서 방어된다.

요약: 금융 도메인에서는 **"빠른 락"보다 "정합성을 보장하는 락"** 을 우선하고, Redis는 멱등 체크 보조 수단으로만 활용한다.
