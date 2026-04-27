# 캐싱 (Caching)

캐시는 성능 향상과 부하 감소를 목표로 합니다. 캐시를 사용하는 양상이 서비스에 큰 영향을 끼치기도 하므로 캐싱 전략을 이해하는 것은 중요합니다.

## 캐싱 전략

### 읽기 전략: Cache-Aside (Lazy Loading)

**Cache Aside 방식**은 캐시 히트 시 캐시에서 데이터를 불러오며, 캐시 미스 발생 시 원본 데이터베이스에서 조회하여 반환합니다. 애플리케이션은 캐시 미스가 발생하면 해당 데이터를 캐시에 적재합니다.

```
1. 캐시 조회
2. 캐시 히트 → 캐시에서 데이터 반환
3. 캐시 미스 → 원본 DB에서 조회 → 캐시에 적재 → 반환
```

**장점:**
- 실제 요청된 데이터만 캐시에 저장되므로 불필요한 데이터 캐싱을 줄일 수 있습니다.
- 캐시에 문제가 발생해도 애플리케이션은 원본 데이터베이스에 직접 접근할 수 있기 때문에 서비스가 계속 작동할 수 있습니다.

**단점:**
- 캐시 미스가 발생하는 경우에만 데이터를 캐시에 적재하기 때문에 원본 데이터베이스와 같은 데이터가 아닐 수도 있습니다. (캐시 불일치)
- 초기에는 대량의 캐시 미스로 인한 데이터베이스 부하가 발생할 수 있습니다.

### 쓰기 전략

**캐시 불일치(Cache Inconsistency)** 란 원본 데이터베이스에 저장된 데이터와 캐시에 저장된 데이터가 서로 다른 상황을 의미합니다.

#### Write Through

원본 데이터에 대한 변경분이 생긴 경우, 매번 캐시에 해당 데이터를 찾아 **함께 변경**하는 방식입니다.

- 2번 쓰기가 발생하지만, 캐시는 항상 최신 데이터를 가지고 있습니다.
- 캐시는 다시 조회되는 경우에 빛을 발휘합니다.
- 무작정 데이터를 갱신하거나 저장하는 방식은 리소스 낭비가 될 수 있으니 만료 시간(TTL)을 사용하는 것이 권장됩니다.

#### Cache Invalidation (캐시 무효화)

원본 데이터에 대한 변경분이 생긴 경우, **캐시 데이터를 만료(삭제)** 시키는 방식입니다.

- Write Through 방식의 단점을 보완한 방식입니다.
- 캐시를 삭제해 다음 읽기에서 원본 저장소를 다시 조회하도록 유도합니다.
- 다만 DB 업데이트와 캐시 삭제 사이에 다른 요청이 읽으면 stale 데이터를 볼 수 있고, 분산 환경에서는 삭제/갱신 순서 경쟁이 생길 수 있습니다.

#### Write Behind (Write Back)

원본 데이터에 대한 변경분이 생긴 경우, **캐시를 먼저 업데이트한 이후 추후에 원본 데이터를 변경**합니다.

- 디스크 쓰기 작업을 비동기 작업으로 수행하여 성능을 개선할 수 있습니다.
- 원본 데이터와 캐시가 일시적으로 일치하지 않을 수 있습니다.
- 캐시 또는 비동기 write-back worker가 장애 나면 DB에 아직 반영되지 않은 최신 쓰기 데이터가 유실될 수 있습니다.
- **쓰기 작업이 빈번하며 일시적인 캐시 불일치를 허용하는 서비스**에서 유용하게 사용될 수 있습니다.

참고:
- [maeil-mail: 캐싱 전략에 대해서 설명해주세요](https://www.maeil-mail.kr/question/132)
- [잘못된 캐싱 전략이 당신의 서비스를 망치고 있습니다](https://maily.so/devpill/posts/8do7dxleogq)

---

## 캐시 스탬피드 (Cache Stampede) / Thundering Herd

대규모 트래픽 환경에서 캐시를 운용하는데, Cache Aside 전략을 사용한다고 가정했을 때, **수많은 요청들이 동시에 캐시 미스를 확인하고 원본 저장소에서 데이터를 가져와 캐시에 적재하는 상황**이 발생할 수 있습니다. 이를 **캐시 스탬피드 현상 혹은 Thundering Herd 문제**라고 표현합니다.

- 캐시 스탬피드 현상은 원본 데이터베이스와 캐시의 성능을 저하할 수도 있습니다.

### 해결 방법

#### 1. 잠금 (Locking)

- 한 요청 처리 스레드가 해당 캐시 키에 대한 잠금을 획득합니다.
- 다른 요청 처리 스레드들은 잠시 대기합니다.
- 잠금을 획득한 스레드는 사용자 요청에 응답하는 과정 동안 캐시 적재 작업은 비동기 스레드로 처리할 수 있습니다.

**단점:**
- 잠금 사용으로 인한 성능 저하 가능성이 존재합니다.
- 잠금 획득 스레드의 실패, 잠금의 생명 주기, 데드락 등 다양한 상황을 고려해야 합니다.

#### 2. 외부 재계산 (External Recomputation)

- 모든 요청 처리 스레드가 캐시 적재를 수행하지 않습니다.
- 캐시를 주기적으로 모니터링하는 스레드를 별도로 관리하여 캐시의 만료시간이 얼마 남지 않은 경우, 데이터를 갱신하여 문제를 예방합니다.

**단점:**
- 다시 사용되지 않을 데이터를 포함하여 갱신하기 때문에 메모리에 대한 불필요한 연산이 발생합니다.
- 메모리 공간을 비효율적으로 사용할 가능성이 존재합니다.

#### 3. 확률적 조기 재계산 (Probabilistic Early Recomputation, PER)

- 캐시 만료 시간이 얼마 남지 않았을 경우, **확률**이라는 개념을 사용하여 여러 요청 처리 스레드 중에서 적은 수만이 캐시를 적재하는 작업을 수행합니다.
- 스탬피드 현상을 완화할 수 있습니다.

참고:
- [maeil-mail: 캐시 스탬피드 현상에 대하여 설명해주세요](https://www.maeil-mail.kr/question/175)
- [라인 기술 블로그 - req-shield로 캐시의 골칫거리 'Thundering Herd 문제' 쉽게 풀기!](https://techblog.lycorp.co.jp/ko/req-saver-for-thundering-herd-problem-in-cache)
- [화해 기술 블로그 - 캐시 스탬피드를 대응하는 성능 향상 전략, PER 알고리즘 구현](https://blog.hwahae.co.kr/all/tech/14003)

---

## Redis 싱글 스레드 특징

Redis가 단일 스레드(single-threaded)로 설계된 이유는 주로 성능 최적화, 복잡성 감소, 그리고 데이터 일관성 유지에 있습니다.

### 싱글 스레드로 설계된 이유

#### 1. 단순한 설계와 구현

단일 스레드 모델은 멀티스레드 모델에 비해 설계와 구현이 상대적으로 간단합니다. 멀티스레드 환경에서는 동시성 문제(레이스 컨디션, 데드락 등)를 처리하기 위해 복잡한 동기화 메커니즘이 필요하지만, 단일 스레드 환경에서는 이런 문제를 자연스럽게 회피할 수 있습니다.

#### 2. 데이터 일관성 보장

동시에 여러 스레드가 동일한 데이터를 수정하려고 할 때 발생할 수 있는 데이터 불일치 문제를 방지합니다. 모든 명령어가 순차적으로 처리되기 때문에, 복잡한 락(lock) 메커니즘 없이도 데이터의 일관성(Atomic)을 자연스럽게 유지할 수 있습니다.

#### 3. 성능 최적화 (컨텍스트 스위칭 최소화)

Redis는 주로 메모리 내에서 빠르게 수행되는 I/O 작업을 처리하는 인메모리 데이터베이스로 설계되어, 매우 빠른 응답 시간을 제공합니다. 단일 스레드 이벤트 루프(event loop)를 사용함으로써 **컨텍스트 스위칭(Context Switching)에 소요되는 오버헤드를 최소화**할 수 있습니다.

#### 4. 이벤트 기반 아키텍처로 높은 동시성 구현

Redis는 이벤트 기반(event-driven) 아키텍처를 채택하여 네트워크 요청을 효율적으로 처리합니다. 단일 스레드 이벤트 루프는 비동기적으로 여러 클라이언트의 요청을 처리할 수 있으며, 이를 통해 높은 동시성을 구현할 수 있습니다.

### Redis 6.0 이후의 변화

Redis 6.0부터 클라이언트로부터 전송된 **네트워크를 읽는 부분과 전송하는 I/O 부분은 멀티 스레드를 지원**합니다. 하지만 실행하는 부분은 싱글 스레드로 동작하기 때문에 기존과 같이 Atomic을 보장합니다.

참고:
- [maeil-mail: Redis가 싱글 스레드로 만들어진 이유를 설명해주세요](https://www.maeil-mail.kr/question/185)
- [Redis - Diving Into Redis 6.0](https://redis.io/blog/diving-into-redis-6/)
- [입 개발 - Redis 6.0 ThreadedIO를 알아보자](https://charsyam.wordpress.com/2020/05/05/%EC%9E%85-%EA%B0%9C%EB%B0%9C-redis-6-0-threadedio%EB%A5%BC-%EC%95%8C%EC%95%84%EB%B3%B4%EC%9E%90/)

---

## Cache Stampede(Thundering Herd) 실전 구현

### 개요
Cache Stampede: 캐시 만료 시 다수의 요청이 동시에 DB/계산에 접근하는 현상.
- 원인: TTL 만료 순간 모든 캐시 미스 요청이 소스로 직행
- 영향: DB 과부하, 레이턴시 급증, 서비스 장애
- 발생 빈도: 트래픽 피크 + 인기 키(hot key) 조합에서 치명적

### 해결 전략 1: Mutex Lock (단일 갱신)

Redis SETNX로 "재계산 중" 플래그를 세운 뒤 나머지 요청은 대기:

```java
public String getWithMutex(String key, Duration ttl) {
    String value = redis.get(key);
    if (value != null) return value;

    String lockKey = key + ":lock";
    // Lua script로 원자적 SETNX + EX
    boolean acquired = redis.setIfAbsent(lockKey, "1", Duration.ofSeconds(5));
    if (acquired) {
        try {
            String computed = compute(key);      // DB 조회 또는 계산
            redis.set(key, computed, ttl);
            return computed;
        } finally {
            redis.delete(lockKey);
        }
    } else {
        // 락 획득 실패 → 잠깐 대기 후 재시도 (또는 stale 값 반환)
        Thread.sleep(50);
        return redis.get(key);  // 재시도
    }
}
```

**단점**: 스핀 대기(spin-wait), 락 보유자 장애 시 TTL 만료까지 블로킹

### 해결 전략 2: singleflight (Go 패턴, Java 적용)

동일 키에 대한 중복 계산 요청을 하나로 합침 (Coalescing):

```java
// Caffeine 기반 유사 패턴 (AsyncLoadingCache)
AsyncLoadingCache<String, String> cache = Caffeine.newBuilder()
    .expireAfterWrite(ttl)
    .buildAsync(key -> computeAsync(key));  // 동일 키 중복 계산 자동 합침

// Guava CacheLoader도 유사하게 동작
```

Go `singleflight.Group`의 Java 적용: Guava의 `LoadingCache`, Caffeine `AsyncLoadingCache` 는 동일 키에 대한 로딩을 단 하나의 Future로 합쳐준다.

### 해결 전략 3: Stale-While-Revalidate

만료 직전 백그라운드 갱신, 요청은 stale(오래된) 값으로 즉시 응답:

```java
// soft TTL (만료 임박 감지) + hard TTL (실제 만료)
record CacheEntry(String value, Instant softExpiry, Instant hardExpiry) {}

public String getStaleWhileRevalidate(String key) {
    CacheEntry entry = redis.get(key);
    if (entry == null) return compute(key);  // cold start

    Instant now = Instant.now();
    if (now.isAfter(entry.softExpiry()) && !isRevalidating(key)) {
        // 백그라운드 갱신 시작 (요청은 stale 값 즉시 반환)
        CompletableFuture.runAsync(() -> {
            String fresh = compute(key);
            redis.set(key, fresh, hardTtl);
        });
    }
    return entry.value();  // stale이라도 즉시 반환
}
```

**장점**: 응답 레이턴시 최소화, 사용자는 항상 즉각 응답
**단점**: 일시적으로 오래된 값 반환 (허용 가능한 데이터 신선도 필요)

### 해결 전략 4: XFetch 확률적 조기 갱신

```
TTL 만료 전에 확률적으로 미리 갱신하는 알고리즘
마지막 실제 재계산 소요 시간 delta와 β에 비례한 확률로 조기 갱신

수식: `β * delta * (-log(random)) > 남은 TTL` 이면 갱신
```

```java
record XFetchEntry(String value, Instant expiresAt, long lastComputeMs) {}

public String getXFetch(String key, double beta) {
    XFetchEntry entry = redis.get(key);
    if (entry == null) {
        long start = System.currentTimeMillis();
        String value = compute(key);
        long deltaMs = System.currentTimeMillis() - start;
        redis.set(key, new XFetchEntry(value, Instant.now().plus(originalTtl), deltaMs), originalTtl);
        return value;
    }

    long ttlMs = Duration.between(Instant.now(), entry.expiresAt()).toMillis();
    long deltaMs = entry.lastComputeMs();  // 마지막 실제 재계산 소요 시간

    // XFetch 확률: 만료가 가까울수록 갱신 확률 증가
    double rand = -Math.log(Math.random());
    if (deltaMs * beta * rand > ttlMs) {
        long start = System.currentTimeMillis();
        String fresh = compute(key);
        long freshDeltaMs = System.currentTimeMillis() - start;
        redis.set(key, new XFetchEntry(fresh, Instant.now().plus(originalTtl), freshDeltaMs), originalTtl);
        return fresh;
    }
    return entry.value();
}
```

**장점**: 만료 직전 단 하나의 요청만 재계산 → stampede 없음
**단점**: beta 튜닝 필요, 구현 복잡

### 전략 비교

| 전략 | Stampede 방지 | 구현 복잡도 | 레이턴시 영향 | 적합한 상황 |
|---|---|---|---|---|
| Mutex Lock | 완전 방지 | 중 | 대기 지연 발생 | 재계산 비용 매우 높을 때 |
| singleflight | 완전 방지 | 낮음 (프레임워크) | 동기 대기 | 동일 JVM 내 로컬 캐시 |
| Stale-While-Revalidate | 대부분 방지 (갱신 구간) | 중 | 영향 없음 | 데이터 신선도 허용 시 |
| XFetch | 실질적 방지 | 높음 | 소폭 영향 | TTL 기반 분산 캐시 |

### 면접 포인트

- Q: Cache Stampede가 DB에 미치는 영향과 방지 방법은?
- Q: stale-while-revalidate와 mutex 락 방식의 트레이드오프는?
- Q: XFetch 수식에서 β 값이 클수록 어떻게 동작하는가?
