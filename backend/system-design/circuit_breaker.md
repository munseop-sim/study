# Circuit Breaker 패턴

---

## 1. Circuit Breaker 패턴 개요

### 문제: 연쇄 장애 (Cascading Failure)

마이크로서비스 환경에서 외부 서비스가 장애 상태일 때, 호출하는 측에서 **타임아웃까지 대기**하면서 스레드와 커넥션이 고갈됩니다. 이로 인해 호출하는 서비스 자체도 응답 불능 상태에 빠지고, 이 장애가 **연쇄적으로 전파(Cascading Failure)** 되어 전체 시스템이 마비될 수 있습니다.

```
[사용자] → [서비스 A] → [서비스 B] → [서비스 C (장애)]
                ↑               ↑
            스레드 고갈       스레드 고갈
          → 서비스 A도 장애  → 서비스 B도 장애
```

### 해결: 빠른 실패 (Fail-Fast)

**Circuit Breaker 패턴**은 외부 서비스 호출의 실패율을 모니터링하다가 임계치를 초과하면, 더 이상 외부 호출을 시도하지 않고 **즉시 실패를 반환(Fail-Fast)** 하여 시스템을 보호합니다.

이 패턴의 이름은 **전기 회로의 차단기(Circuit Breaker)** 에서 유래했습니다. 과전류가 흐르면 차단기가 회로를 끊어 전체 시스템을 보호하는 원리와 동일합니다.

### 핵심 이점

- **장애 전파 차단**: 외부 서비스 장애가 내부 시스템으로 전파되는 것을 방지
- **리소스 보호**: 스레드, 커넥션 등의 리소스가 대기 상태에서 낭비되는 것을 방지
- **빠른 응답**: 사용자에게 타임아웃 대기 없이 빠른 응답(fallback) 제공
- **자동 복구**: 외부 서비스가 복구되면 자동으로 정상 상태로 전환

---

## 2. 3가지 상태 전이

Circuit Breaker는 **Closed**, **Open**, **Half-Open** 3가지 상태를 가지며, 상황에 따라 상태가 전이됩니다.

### 상태 전이 다이어그램

```
                    실패율 임계치 초과
        ┌──────────────────────────────────┐
        │                                  ▼
   ┌─────────┐                        ┌─────────┐
   │ CLOSED  │                        │  OPEN   │
   │ (정상)  │                        │ (차단)  │
   └─────────┘                        └─────────┘
        ▲                                  │
        │         wait duration 경과       │
        │                                  ▼
        │                           ┌───────────┐
        │    시험 요청 성공          │ HALF-OPEN │
        └───────────────────────────│  (시험)   │
                                    └───────────┘
                                         │
                                         │ 시험 요청 실패
                                         ▼
                                    ┌─────────┐
                                    │  OPEN   │
                                    │ (차단)  │
                                    └─────────┘
```

### 각 상태 상세

| 상태 | 설명 | 동작 |
|---|---|---|
| **Closed (정상)** | 기본 상태. 모든 요청이 외부 서비스로 정상 전달됨 | 실패율을 지속적으로 모니터링 |
| **Open (차단)** | 장애 감지 상태. 외부 호출을 차단하고 즉시 실패 반환 | Fallback 응답 반환, 일정 시간(wait duration) 대기 |
| **Half-Open (시험)** | 복구 확인 상태. 제한된 수의 시험 요청만 통과 | 시험 요청의 성공/실패에 따라 Closed 또는 Open으로 전이 |

### 상태 전이 조건

| 전이 | 조건 | 예시 |
|---|---|---|
| Closed → Open | 실패율(failure rate)이 임계치를 초과 | 최근 10건 중 5건 실패 (50% 초과) |
| Open → Half-Open | 대기 시간(wait duration)이 경과 | Open 상태에서 60초 경과 |
| Half-Open → Closed | 시험 요청이 성공 | 허용된 시험 호출이 모두 성공 |
| Half-Open → Open | 시험 요청이 실패 | 시험 호출 중 하나라도 실패 |

---

## 3. Resilience4j 구현

**Resilience4j**는 Java 기반의 경량 장애 허용(fault tolerance) 라이브러리로, Circuit Breaker, Retry, Rate Limiter, Bulkhead 등의 패턴을 제공합니다.

### 의존성 추가 (Gradle)

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.github.resilience4j:resilience4j-spring-boot3:2.2.0")
    implementation("org.springframework.boot:spring-boot-starter-aop")
}
```

### Spring Boot 설정 (application.yml)

```yaml
resilience4j:
  circuitbreaker:
    instances:
      paymentService:
        # 슬라이딩 윈도우 타입: COUNT_BASED(호출 수 기반) vs TIME_BASED(시간 기반)
        slidingWindowType: COUNT_BASED
        # 슬라이딩 윈도우 크기: 최근 N건의 호출을 모니터링
        slidingWindowSize: 10
        # 실패율 임계치 (%): 이 비율을 초과하면 Open 상태로 전이
        failureRateThreshold: 50
        # Open 상태 유지 시간: 이 시간이 지나면 Half-Open으로 전이
        waitDurationInOpenState: 60000  # 60초
        # Half-Open 상태에서 허용할 시험 호출 수
        permittedNumberOfCallsInHalfOpenState: 3
        # 느린 호출 비율 임계치 (%): 느린 호출 비율이 이 값을 초과하면 Open
        slowCallRateThreshold: 80
        # 느린 호출 판단 기준 시간: 이 시간을 초과하면 "느린 호출"로 판정
        slowCallDurationThreshold: 3000  # 3초
        # 최소 호출 수: 이 수 이상 호출되어야 실패율을 계산
        minimumNumberOfCalls: 5
        # Circuit Breaker가 감지할 예외 목록
        recordExceptions:
          - java.io.IOException
          - java.net.SocketTimeoutException
          - org.springframework.web.client.HttpServerErrorException
        # 실패로 기록하지 않을 예외 목록
        ignoreExceptions:
          - com.example.BusinessException
```

### 주요 설정값 상세

| 설정 | 기본값 | 설명 |
|---|---|---|
| `slidingWindowType` | `COUNT_BASED` | `COUNT_BASED`: 최근 N건 호출 기반, `TIME_BASED`: 최근 N초 기반 |
| `slidingWindowSize` | 100 | 모니터링 윈도우 크기 (건수 또는 초) |
| `failureRateThreshold` | 50 | 실패율 임계치 (%), 초과 시 Open |
| `waitDurationInOpenState` | 60000ms | Open 상태 유지 시간 |
| `permittedNumberOfCallsInHalfOpenState` | 10 | Half-Open에서 허용할 시험 호출 수 |
| `slowCallRateThreshold` | 100 | 느린 호출 비율 임계치 (%) |
| `slowCallDurationThreshold` | 60000ms | 느린 호출 판단 기준 시간 |
| `minimumNumberOfCalls` | 100 | 실패율 계산을 위한 최소 호출 수 |

### @CircuitBreaker 어노테이션 사용법 (Java)

```java
@Service
@Slf4j
public class PaymentService {

    private final PaymentGatewayClient paymentGatewayClient;

    @CircuitBreaker(name = "paymentService", fallbackMethod = "paymentFallback")
    public PaymentResponse processPayment(PaymentRequest request) {
        return paymentGatewayClient.requestPayment(request);
    }

    /**
     * Fallback 메서드: Circuit Breaker가 Open 상태이거나 호출 실패 시 호출됨
     * - 원본 메서드와 동일한 파라미터 + 마지막에 Throwable 파라미터 추가
     * - 반환 타입은 원본 메서드와 동일해야 함
     */
    private PaymentResponse paymentFallback(PaymentRequest request, Throwable t) {
        log.warn("결제 서비스 Circuit Breaker 작동. fallback 처리. 원인: {}", t.getMessage());
        return PaymentResponse.fail("결제 서비스가 일시적으로 불안정합니다. 잠시 후 다시 시도해주세요.");
    }
}
```

### @CircuitBreaker 어노테이션 사용법 (Kotlin)

```kotlin
@Service
class PaymentService(
    private val paymentGatewayClient: PaymentGatewayClient
) {
    companion object {
        private val log = LoggerFactory.getLogger(PaymentService::class.java)
    }

    @CircuitBreaker(name = "paymentService", fallbackMethod = "paymentFallback")
    fun processPayment(request: PaymentRequest): PaymentResponse {
        return paymentGatewayClient.requestPayment(request)
    }

    private fun paymentFallback(request: PaymentRequest, t: Throwable): PaymentResponse {
        log.warn("결제 서비스 Circuit Breaker 작동. fallback 처리. 원인: {}", t.message)
        return PaymentResponse.fail("결제 서비스가 일시적으로 불안정합니다. 잠시 후 다시 시도해주세요.")
    }
}
```

### 프로그래밍 방식 설정 (Java)

어노테이션 대신 직접 CircuitBreaker 인스턴스를 생성하여 사용할 수도 있습니다.

```java
@Configuration
public class CircuitBreakerConfig {

    @Bean
    public CircuitBreaker paymentCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .slidingWindowType(SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(10)
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofSeconds(60))
            .permittedNumberOfCallsInHalfOpenState(3)
            .slowCallRateThreshold(80)
            .slowCallDurationThreshold(Duration.ofSeconds(3))
            .recordExceptions(IOException.class, SocketTimeoutException.class)
            .build();

        return registry.circuitBreaker("paymentService", config);
    }
}
```

```java
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final CircuitBreaker paymentCircuitBreaker;
    private final PaymentGatewayClient paymentGatewayClient;

    public PaymentResponse processPayment(PaymentRequest request) {
        Supplier<PaymentResponse> decoratedSupplier = CircuitBreaker
            .decorateSupplier(paymentCircuitBreaker, () -> paymentGatewayClient.requestPayment(request));

        return Try.ofSupplier(decoratedSupplier)
            .recover(throwable -> PaymentResponse.fail("결제 서비스 일시 불안정"))
            .get();
    }
}
```

---

## 4. Fallback 전략

Circuit Breaker가 Open 상태일 때, 또는 외부 호출이 실패했을 때 **어떤 대안 응답을 반환할 것인지** 결정하는 것이 Fallback 전략입니다.

### 4.1 기본값 반환

가장 단순한 방식으로, 캐시된 데이터나 빈 목록 등의 기본값을 반환합니다.

```java
// 환율 조회 실패 시 마지막으로 성공한 캐시 데이터 반환
private ExchangeRateResponse getExchangeRateFallback(String currency, Throwable t) {
    ExchangeRateResponse cached = exchangeRateCache.get(currency);
    if (cached != null) {
        return cached;  // 캐시된 환율 반환
    }
    return ExchangeRateResponse.empty();  // 캐시도 없으면 빈 응답
}
```

### 4.2 대체 서비스 호출

Primary 서비스가 불가능할 때 Secondary 서비스를 호출합니다.

```java
// Primary 인증 서비스 실패 시 Secondary 인증 서비스로 대체
@CircuitBreaker(name = "primaryAuth", fallbackMethod = "authFallback")
public AuthResponse authenticate(AuthRequest request) {
    return primaryAuthClient.authenticate(request);
}

private AuthResponse authFallback(AuthRequest request, Throwable t) {
    log.warn("Primary 인증 서비스 장애, Secondary로 전환");
    return secondaryAuthClient.authenticate(request);
}
```

### 4.3 에러 메시지 반환

사용자에게 적절한 안내 메시지를 반환합니다.

```java
private NotificationResponse sendNotificationFallback(NotificationRequest request, Throwable t) {
    log.error("알림 서비스 장애 발생: {}", t.getMessage());
    return NotificationResponse.builder()
        .success(false)
        .message("알림 서비스가 일시적으로 불안정합니다. 알림은 서비스 복구 후 발송됩니다.")
        .build();
}
```

### 4.4 큐에 저장 후 나중에 재처리

실패한 요청을 메시지 큐에 저장하고, 외부 서비스 복구 후 재처리합니다.

```java
private PayoutResponse payoutFallback(PayoutRequest request, Throwable t) {
    log.warn("송금 서비스 장애. 재처리 큐에 저장: {}", request.getTransactionId());
    // 재처리 큐(Kafka, RabbitMQ 등)에 메시지 발행
    retryQueueProducer.send(new RetryMessage(request, "payout", Instant.now()));
    return PayoutResponse.builder()
        .status(PayoutStatus.PENDING_RETRY)
        .message("송금 처리가 지연되고 있습니다. 잠시 후 자동으로 재시도됩니다.")
        .build();
}
```

### Fallback 전략 선택 기준

| 전략 | 적합한 상황 | 예시 |
|---|---|---|
| 기본값 반환 | 데이터의 정확도보다 가용성이 중요한 경우 | 환율 조회, 추천 목록 |
| 대체 서비스 호출 | 동일한 기능을 제공하는 백업 서비스가 존재하는 경우 | 인증, 결제 게이트웨이 |
| 에러 메시지 반환 | 즉시 대체 불가능하지만 사용자 안내가 필요한 경우 | 알림 발송, 보고서 생성 |
| 큐 저장 후 재처리 | 요청 유실이 허용되지 않는 비즈니스 크리티컬 작업 | 송금, 결제, 정산 |

---

## 5. 관련 패턴 비교

### Circuit Breaker vs Retry

| 구분 | Circuit Breaker | Retry |
|---|---|---|
| **목적** | 반복적인 실패를 감지하고 호출 자체를 차단 | 일시적인 실패에 대해 재시도하여 성공 유도 |
| **동작 방식** | 실패율 모니터링 → 임계치 초과 시 차단 | 실패 시 일정 횟수/간격으로 재시도 |
| **적합한 상황** | 외부 서비스가 **지속적으로** 장애인 경우 | 네트워크 순간 끊김 등 **일시적** 장애 |
| **조합 사용** | Retry 안에 Circuit Breaker를 래핑하여 함께 사용 가능 | Circuit Breaker 내부에서 Retry 적용 |

```java
// Retry + Circuit Breaker 조합 사용 예시
// Retry가 먼저 적용되고, 재시도에도 실패하면 Circuit Breaker가 실패로 기록
@Retry(name = "paymentService")
@CircuitBreaker(name = "paymentService", fallbackMethod = "fallback")
public PaymentResponse processPayment(PaymentRequest request) {
    return paymentGatewayClient.requestPayment(request);
}
```

### Circuit Breaker vs Rate Limiter

| 구분 | Circuit Breaker | Rate Limiter |
|---|---|---|
| **보호 대상** | **외부 서비스** (호출하는 대상) | **자체 서비스** (호출받는 대상) |
| **동작 방향** | 나가는(outgoing) 요청을 제어 | 들어오는(incoming) 요청을 제어 |
| **트리거** | 외부 서비스의 실패율/응답 시간 | 요청 빈도(초당/분당 요청 수) |
| **목적** | 장애 전파 방지 | 과부하 방지 |

### Bulkhead 패턴

**Bulkhead(격벽) 패턴**은 선박의 격벽에서 유래한 패턴으로, 리소스를 격리하여 하나의 장애가 전체 시스템으로 전파되는 것을 방지합니다.

```
┌─────────────────────────────────────────┐
│              애플리케이션               │
│  ┌──────────────┐  ┌──────────────┐    │
│  │ 결제 서비스   │  │ 알림 서비스   │    │
│  │ 스레드 풀: 10 │  │ 스레드 풀: 5  │    │
│  │              │  │              │    │
│  │ ← 장애 발생  │  │ ← 영향 없음  │    │
│  └──────────────┘  └──────────────┘    │
└─────────────────────────────────────────┘
```

- **스레드 풀 격리**: 서비스별로 별도의 스레드 풀을 할당하여, 한 서비스의 스레드 고갈이 다른 서비스에 영향을 주지 않음
- **세마포어 격리**: 동시 호출 수를 제한하여 리소스를 보호

```yaml
# Resilience4j Bulkhead 설정
resilience4j:
  bulkhead:
    instances:
      paymentService:
        maxConcurrentCalls: 10    # 최대 동시 호출 수
        maxWaitDuration: 500ms    # 최대 대기 시간
  thread-pool-bulkhead:
    instances:
      paymentService:
        maxThreadPoolSize: 10
        coreThreadPoolSize: 5
        queueCapacity: 20
```

### 패턴 조합 정리

| 패턴 | 역할 | 비유 |
|---|---|---|
| **Circuit Breaker** | 장애 감지 시 호출 차단 | 전기 차단기 |
| **Retry** | 일시적 실패 재시도 | 재다이얼 |
| **Rate Limiter** | 요청 빈도 제한 | 고속도로 톨게이트 |
| **Bulkhead** | 리소스 격리 | 선박 격벽 |
| **Time Limiter** | 호출 시간 제한 | 타이머 |

이 패턴들은 **독립적으로 사용할 수도 있고, 조합하여 사용**할 수도 있습니다. Resilience4j에서는 다음 순서로 적용됩니다:

```
Retry → CircuitBreaker → RateLimiter → TimeLimiter → Bulkhead → 실제 호출
```

---

## 6. 실무 적용 사례

### 6.1 외부 API 호출

MSA 환경에서 외부 서비스(결제, 인증, 알림 등)를 호출할 때 Circuit Breaker를 적용하여 장애 전파를 방지합니다.

**결제 게이트웨이 (PortOne 등)**

```kotlin
@Service
class PaymentGatewayService(
    private val portOneClient: PortOneClient,
    private val paymentCache: PaymentCacheService
) {
    @CircuitBreaker(name = "portone", fallbackMethod = "paymentFallback")
    fun requestPayment(request: PaymentRequest): PaymentResponse {
        return portOneClient.processPayment(request)
    }

    private fun paymentFallback(request: PaymentRequest, t: Throwable): PaymentResponse {
        log.warn("PortOne 결제 서비스 장애. 재처리 큐에 저장: txId={}", request.transactionId)
        retryQueueProducer.send(RetryMessage(request))
        return PaymentResponse.pendingRetry("결제 처리가 지연되고 있습니다.")
    }
}
```

**reCAPTCHA 인증**

```java
@Service
public class RecaptchaService {

    @CircuitBreaker(name = "recaptcha", fallbackMethod = "recaptchaFallback")
    public RecaptchaResult verify(String token) {
        return recaptchaClient.verify(token);
    }

    // reCAPTCHA 장애 시 다른 인증 수단으로 대체하거나, 일시적으로 통과 처리
    private RecaptchaResult recaptchaFallback(String token, Throwable t) {
        log.warn("reCAPTCHA 서비스 장애. 대체 인증으로 전환");
        return RecaptchaResult.bypassWithWarning();
    }
}
```

### 6.2 MSA 서비스 간 통신

서비스 간 REST/gRPC 통신에 Circuit Breaker를 적용합니다.

```yaml
# 서비스별 Circuit Breaker 인스턴스 분리
resilience4j:
  circuitbreaker:
    instances:
      memberService:
        slidingWindowSize: 10
        failureRateThreshold: 50
        waitDurationInOpenState: 30000
      walletService:
        slidingWindowSize: 20
        failureRateThreshold: 40
        waitDurationInOpenState: 60000
      tradeService:
        slidingWindowSize: 15
        failureRateThreshold: 60
        waitDurationInOpenState: 45000
```

### 6.3 모니터링 연동

Circuit Breaker의 상태 변화를 모니터링하여 장애를 빠르게 인지하고 대응합니다.

```java
@Component
public class CircuitBreakerEventListener {

    @EventListener
    public void onStateTransition(CircuitBreakerOnStateTransitionEvent event) {
        log.warn("[Circuit Breaker] {} 상태 전이: {} → {}",
            event.getCircuitBreakerName(),
            event.getStateTransition().getFromState(),
            event.getStateTransition().getToState());

        // 슬랙 알림, 메트릭 수집 등
        if (event.getStateTransition().getToState() == State.OPEN) {
            alertService.sendAlert("Circuit Breaker OPEN: " + event.getCircuitBreakerName());
        }
    }
}
```

Resilience4j는 **Micrometer**와 연동하여 Prometheus, Grafana 등으로 메트릭을 수집할 수 있습니다.

```yaml
# Actuator + Micrometer 연동
management:
  endpoints:
    web:
      exposure:
        include: health, circuitbreakers, circuitbreakerevents
  health:
    circuitbreakers:
      enabled: true
```

---

## 면접 예상 질문

**Q. Circuit Breaker 패턴이 필요한 이유는?**

> 외부 서비스 장애 시 타임아웃 대기로 인해 스레드/커넥션이 고갈되어 연쇄 장애(Cascading Failure)가 발생할 수 있습니다. Circuit Breaker는 실패율을 모니터링하다가 임계치 초과 시 호출 자체를 차단(Fail-Fast)하여 시스템을 보호합니다.

**Q. 3가지 상태와 전이 조건을 설명해주세요.**

> Closed(정상)에서 실패율이 임계치를 초과하면 Open(차단)으로 전이합니다. Open에서 대기 시간이 경과하면 Half-Open(시험)으로 전이하여 제한된 요청을 통과시킵니다. 시험 요청이 성공하면 Closed로 복귀하고, 실패하면 다시 Open으로 돌아갑니다.

**Q. COUNT_BASED와 TIME_BASED의 차이는?**

> COUNT_BASED는 최근 N건의 호출 결과를 기반으로 실패율을 계산하고, TIME_BASED는 최근 N초 동안의 호출 결과를 기반으로 실패율을 계산합니다. 트래픽이 일정하다면 COUNT_BASED가 적합하고, 트래픽 변동이 크다면 TIME_BASED가 유리합니다.

**Q. Circuit Breaker와 Retry를 함께 사용할 때 주의할 점은?**

> Retry가 Circuit Breaker 바깥에 위치해야 합니다. 즉, Retry가 먼저 재시도를 수행하고, 재시도에도 실패하면 Circuit Breaker가 해당 호출을 실패로 기록합니다. 반대로 Circuit Breaker가 바깥에 있으면 Retry의 각 시도가 모두 실패로 기록되어 의도보다 빨리 Open 상태로 전이될 수 있습니다.

---

## 참고 자료

- [Resilience4j 공식 문서](https://resilience4j.readme.io/docs/circuitbreaker)
- [Martin Fowler - Circuit Breaker](https://martinfowler.com/bliki/CircuitBreaker.html)
- [maeil-mail: 서킷 브레이커(Circuit Breaker) 패턴에 대해 설명해주세요](https://www.maeil-mail.kr/question/211)
- [우아한형제들 기술 블로그 - 회복탄력성 있는 시스템을 위한 서킷 브레이커](https://techblog.woowahan.com/15694/)
- [Spring Cloud Circuit Breaker 공식 문서](https://spring.io/projects/spring-cloud-circuitbreaker)
