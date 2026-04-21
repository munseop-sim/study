# 관찰성(Observability) 스택 — Logs, Metrics, Traces

## 1. 관찰성(Observability)이란

**모니터링(Monitoring)**: 미리 정의된 지표를 보는 것. "CPU가 90%인가?"
**관찰성(Observability)**: 시스템의 내부 상태를 외부 출력으로부터 추론하는 능력. "왜 이 요청만 느린가?"

분산 시스템에서는 단순 모니터링만으로는 장애 원인을 파악하기 어렵다.
관찰성은 **Logs + Metrics + Traces** 세 가지 시그널로 구성된다.

---

## 2. 3 Pillars of Observability

### 2.1 Logs (로그) — 이벤트 기록

로그는 시스템에서 발생한 이산적 이벤트를 시간 순서로 기록한다.

#### 구조화 로그 (JSON)

비구조화 로그는 검색이 어렵다.

```
# 나쁜 예: 비구조화 로그
2024-01-15 10:23:45 ERROR 출금 실패 사용자 12345 금액 50000

# 좋은 예: 구조화 로그 (JSON)
{
  "timestamp": "2024-01-15T10:23:45.123+09:00",
  "level": "ERROR",
  "logger": "WithdrawService",
  "message": "출금 처리 실패",
  "userId": "12345",
  "amount": "50000",
  "walletId": "w-789",
  "errorCode": "WLT-4001",
  "traceId": "abc123def456",
  "spanId": "span789",
  "service": "wallet-service",
  "env": "production"
}
```

#### 로그 레벨

| 레벨 | 용도 | 예시 |
|---|---|---|
| DEBUG | 개발 시 상세 정보. 운영 환경에서 OFF | SQL 쿼리, 변수 값 |
| INFO | 정상 흐름의 주요 이벤트 | 출금 요청 수신, 완료 |
| WARN | 오류는 아니지만 주의 필요 | 잔액 부족 시도, 재시도 발생 |
| ERROR | 오류 발생 (복구 가능) | DB 연결 실패 후 재시도 중 |
| FATAL | 시스템 종료 수준의 심각한 오류 | 기동 시 필수 설정 없음 |

#### MDC (Mapped Diagnostic Context)

MDC는 현재 스레드에 컨텍스트 정보를 저장하여 모든 로그에 자동으로 포함시킨다.

```kotlin
// Kotlin + Spring — MDC Filter
@Component
class MdcLoggingFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val traceId = request.getHeader("X-Trace-Id") ?: UUID.randomUUID().toString()
        val userId = SecurityContextHolder.getContext().authentication?.name

        MDC.put("traceId", traceId)
        MDC.put("userId", userId ?: "anonymous")
        MDC.put("requestUri", request.requestURI)

        try {
            filterChain.doFilter(request, response)
        } finally {
            MDC.clear()  // 스레드 풀 재사용으로 인한 오염 방지
        }
    }
}
```

```yaml
# logback-spring.xml 패턴에 MDC 포함
<pattern>{"timestamp":"%d{yyyy-MM-dd'T'HH:mm:ss.SSSZ}","level":"%level","traceId":"%X{traceId}","userId":"%X{userId}","message":"%message"}%n</pattern>
```

---

### 2.2 Metrics (메트릭) — 수치 측정

메트릭은 시스템 상태를 시계열 수치로 표현한다.

#### 메트릭 유형

| 유형 | 설명 | 예시 |
|---|---|---|
| **Counter** | 단조 증가하는 카운터 | 출금 요청 횟수, 에러 발생 횟수 |
| **Gauge** | 현재 값 (증감 가능) | 현재 활성 사용자 수, 큐 크기 |
| **Histogram** | 값의 분포 + 백분위 계산 | 응답 시간 분포, p50/p99 계산 |
| **Summary** | 클라이언트 측 백분위 계산 | 응답 시간 p99 (정확하지만 집계 불가) |

#### Spring Boot Actuator + Micrometer + Prometheus

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: prometheus, health, info
  metrics:
    export:
      prometheus:
        enabled: true
```

```kotlin
// 커스텀 메트릭 정의
@Component
class WithdrawMetrics(private val meterRegistry: MeterRegistry) {

    private val withdrawCounter = Counter.builder("withdraw.requests.total")
        .description("출금 요청 총 횟수")
        .register(meterRegistry)

    private val withdrawFailCounter = Counter.builder("withdraw.failures.total")
        .description("출금 실패 횟수")
        .register(meterRegistry)

    private val withdrawTimer = Timer.builder("withdraw.duration")
        .description("출금 처리 시간")
        .publishPercentiles(0.5, 0.95, 0.99)
        .register(meterRegistry)

    fun recordWithdrawRequest() = withdrawCounter.increment()
    fun recordWithdrawFailure() = withdrawFailCounter.increment()
    fun recordWithdrawDuration(duration: Duration) = withdrawTimer.record(duration)
}
```

#### Prometheus + Grafana 스택

```
Spring Boot App → /actuator/prometheus → Prometheus (수집) → Grafana (시각화)
```

```yaml
# prometheus.yml - 수집 설정
scrape_configs:
  - job_name: 'wallet-service'
    static_configs:
      - targets: ['wallet-service:8080']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 15s
```

---

### 2.3 Traces (분산 추적) — 요청 흐름 추적

분산 시스템에서 하나의 요청이 여러 서비스를 거칠 때 전체 흐름을 추적한다.

#### TraceId / SpanId

```
[사용자 출금 요청]
TraceId: abc-123 (전체 요청을 관통하는 ID)

WalletService (SpanId: s1, ParentSpanId: null)        [0ms ~ 250ms]
├── DB 조회 (SpanId: s2, ParentSpanId: s1)             [5ms ~ 15ms]
├── 잔액 차감 (SpanId: s3, ParentSpanId: s1)           [20ms ~ 50ms]
│   └── DB 업데이트 (SpanId: s4, ParentSpanId: s3)     [22ms ~ 45ms]
└── PayoutService 호출 (SpanId: s5, ParentSpanId: s1)  [100ms ~ 240ms]
    └── 외부 API 호출 (SpanId: s6, ParentSpanId: s5)   [110ms ~ 235ms]
```

어느 서비스에서 병목이 발생하는지 한눈에 파악할 수 있다.

#### OpenTelemetry

```kotlin
// Spring Boot 3.x + Micrometer Tracing (OpenTelemetry 기반)
// build.gradle.kts
dependencies {
    implementation("io.micrometer:micrometer-tracing-bridge-otel")
    implementation("io.opentelemetry.instrumentation:opentelemetry-spring-boot-starter")
}
```

```yaml
# application.yml
management:
  tracing:
    sampling:
      probability: 1.0  # 100% 샘플링 (운영: 0.01~0.1)
spring:
  application:
    name: wallet-service
```

HTTP 헤더를 통해 TraceId를 전파한다.

```
요청 헤더: traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01
           └── traceId: 4bf92f3577b34da6a3ce929d0e0e4736
               └── spanId: 00f067aa0ba902b7
```

#### APM 도구 비교

| 도구 | 특징 | 적합한 상황 |
|---|---|---|
| **Pinpoint** | 오픈소스, Java/Kotlin 특화, 코드 수정 없이 자동 계측 | 자체 운영 선호, Java 환경 |
| **Datadog** | SaaS, 풍부한 대시보드, AI Insights, 모든 언어 지원 | 빠른 도입, 예산 여유 있을 때 |
| **New Relic** | SaaS, APM + 인프라 통합 모니터링 | 전체 스택 통합 관찰성 |
| **Jaeger** | 오픈소스, OpenTelemetry 표준, Trace에 집중 | 트레이싱만 필요, 자체 운영 |
| **Zipkin** | 오픈소스, 경량, Twitter 출신 | 소규모, 단순한 트레이싱 |

---

## 3. SLI / SLO / SLA

### 정의

| 용어 | 의미 | 예시 |
|---|---|---|
| **SLI** (Service Level Indicator) | 서비스 품질을 측정하는 지표 | p99 응답시간, 가용성(%), 에러율 |
| **SLO** (Service Level Objective) | SLI의 목표값 | p99 응답시간 < 500ms, 가용성 99.9% |
| **SLA** (Service Level Agreement) | 고객과의 계약. SLO 위반 시 페널티 발생 | SLO 미달 시 크레딧 환급 |

### 관계

```
SLA (계약)
  └─ SLO (내부 목표, SLA보다 엄격하게 설정)
       └─ SLI (실제 측정값)
```

SLA보다 SLO를 더 엄격하게 설정해야 SLO 위반 전에 대응할 수 있다.

### Error Budget

```
SLO: 가용성 99.9%
→ 월간 허용 다운타임: 43.8분 (30일 × 24시간 × 60분 × 0.1%)
→ 이 43.8분이 "Error Budget"

Error Budget이 소진되면:
  - 신규 기능 배포 동결
  - 안정성 개선 작업 우선
```

---

## 4. 금융 도메인 관찰성 설계

### 4.1 출금 흐름 로깅 포인트

```kotlin
@Service
class WithdrawService(
    private val walletRepository: WalletRepository,
    private val withdrawMetrics: WithdrawMetrics
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun withdraw(command: WithdrawCommand): WithdrawResult {
        val startTime = Instant.now()

        // 포인트 1: 요청 수신
        log.info("출금 요청 수신 walletId={} amount={} requestId={}",
            command.walletId, command.amount, command.requestId)
        withdrawMetrics.recordWithdrawRequest()

        // 포인트 2: 잔액 조회 (락)
        val wallet = walletRepository.findByIdForUpdate(command.walletId)
            ?: run {
                log.warn("지갑 없음 walletId={}", command.walletId)
                return WithdrawResult.NOT_FOUND
            }

        // 포인트 3: 잔액 검증
        if (wallet.balance < command.amount) {
            log.warn("잔액 부족 walletId={} balance={} requestAmount={}",
                command.walletId, wallet.balance, command.amount)
            withdrawMetrics.recordWithdrawFailure()
            return WithdrawResult.INSUFFICIENT_BALANCE
        }

        // 포인트 4: 잔액 차감
        wallet.withdraw(command.amount)
        walletRepository.save(wallet)
        log.info("잔액 차감 완료 walletId={} previousBalance={} newBalance={}",
            command.walletId, wallet.balance + command.amount, wallet.balance)

        // 포인트 5: 완료 + 소요 시간
        val duration = Duration.between(startTime, Instant.now())
        withdrawMetrics.recordWithdrawDuration(duration)
        log.info("출금 처리 완료 walletId={} durationMs={}",
            command.walletId, duration.toMillis())

        return WithdrawResult.SUCCESS
    }
}
```

### 4.2 금융 서비스 핵심 메트릭

```
# 처리량
withdraw_requests_total{service="wallet"} 카운터
withdraw_failures_total{service="wallet", reason="insufficient_balance"} 카운터

# 응답시간 (Histogram으로 p50/p95/p99 계산)
withdraw_duration_seconds{quantile="0.50"} 0.015
withdraw_duration_seconds{quantile="0.95"} 0.080
withdraw_duration_seconds{quantile="0.99"} 0.250

# 잠금 대기시간
db_lock_wait_duration_seconds{quantile="0.99"} 0.050

# 에러율
withdraw_error_rate = withdraw_failures_total / withdraw_requests_total
```

### 4.3 알람 기준 (AlertManager 예시)

```yaml
# prometheus alert rules
groups:
  - name: wallet-service-alerts
    rules:
      # 에러율 급증 알람
      - alert: WithdrawErrorRateHigh
        expr: |
          rate(withdraw_failures_total[5m]) /
          rate(withdraw_requests_total[5m]) > 0.05
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "출금 에러율이 5%를 초과했습니다"

      # 응답시간 급등 알람
      - alert: WithdrawLatencyHigh
        expr: |
          histogram_quantile(0.99,
            rate(withdraw_duration_seconds_bucket[5m])
          ) > 1.0
        for: 3m
        labels:
          severity: warning
        annotations:
          summary: "출금 p99 응답시간이 1초를 초과했습니다"

      # 서비스 다운 알람
      - alert: WalletServiceDown
        expr: up{job="wallet-service"} == 0
        for: 1m
        labels:
          severity: critical
```

### 4.4 Grafana 대시보드 구성

```
Row 1: 서비스 개요
  - 요청 수 (QPS)
  - 에러율 (%)
  - p50/p95/p99 응답시간

Row 2: 비즈니스 메트릭
  - 시간당 출금 건수
  - 잔액 부족 시도 비율
  - 총 출금 금액

Row 3: 인프라
  - DB 연결 풀 사용률
  - DB 쿼리 실행 시간 p99
  - 락 대기 시간
```

---

## 5. 실무 운영 팁

### 로그 볼륨 관리

```yaml
# DEBUG 로그는 샘플링
logging:
  level:
    com.sentbiz: INFO  # 기본 INFO
    # 특정 클래스만 DEBUG
    com.sentbiz.wallet.WithdrawService: DEBUG
```

### 트레이싱 샘플링

```yaml
management:
  tracing:
    sampling:
      probability: 0.05  # 5% 샘플링 (운영 환경)
      # 에러 요청은 항상 샘플링하도록 커스텀 샘플러 추가 권장
```

### 로그 집중화

```
각 서비스 → Logstash / Fluentd → Elasticsearch → Kibana
                                               → OpenSearch
```

---

## 6. 관찰성 3 Pillars 상호보완

세 시그널은 서로 보완한다.

```
알람 발생 (Metrics: 에러율 급증)
    ↓
원인 추적 (Traces: 어느 서비스, 어느 요청?)
    ↓
상세 확인 (Logs: 해당 TraceId로 로그 검색)
```

TraceId를 공통 키로 세 시스템을 연결하는 것이 핵심이다.

---

## 면접 포인트

### Q1. "관찰성(Observability)과 모니터링의 차이는?"
- 모니터링은 미리 알고 있는 것을 감시한다 (Known Unknowns).
- 관찰성은 알 수 없었던 문제도 내부 상태를 외부 시그널로 추론할 수 있게 한다 (Unknown Unknowns).
- 분산 시스템에서는 단순 모니터링만으로는 원인 파악이 어렵기 때문에 Logs, Metrics, Traces를 조합한 관찰성이 필요하다.

### Q2. "SLI, SLO, SLA를 설명하세요."
- SLI는 실제 측정 지표 (예: p99 응답시간 = 300ms).
- SLO는 내부적으로 정한 목표 (예: p99 < 500ms).
- SLA는 고객과의 계약으로 SLO 위반 시 페널티가 있다.
- SLO는 SLA보다 엄격하게 설정해야 위반 전에 대응할 여유가 생긴다. Error Budget으로 신규 기능 배포와 안정성 사이의 균형을 관리한다.

### Q3. "분산 추적(Distributed Tracing)은 어떻게 동작하나요?"
- 각 요청에 고유한 TraceId를 부여하고, 서비스 간 호출 시 HTTP 헤더(traceparent 등)로 전파한다.
- 각 서비스는 자신의 처리 단위(Span)를 TraceId에 연결하여 중앙 수집 서버(Jaeger, Zipkin)에 전송한다.
- 수집된 Span들을 TraceId로 조합하면 전체 요청 흐름과 각 구간의 소요시간을 시각화할 수 있다.

### Q4. "금융 서비스에서 어떤 메트릭을 모니터링해야 하나요?"
- 처리량 메트릭: 출금/입금 요청 건수, QPS
- 성공/실패율: 에러율, 잔액 부족 비율
- 응답시간: p50/p95/p99 레이턴시, 특히 p99가 중요
- 인프라: DB 연결 풀 사용률, 락 대기시간, 커넥션 타임아웃 횟수
- 비즈니스: 시간당 총 출금 금액, 이상 거래 패턴

### Q5. "MDC(Mapped Diagnostic Context)를 어떻게 활용하나요?"
- MDC는 현재 스레드에 키-값 컨텍스트를 저장하여 모든 로그에 자동으로 추가한다.
- 주로 TraceId, userId, requestId 등을 MDC에 저장한다.
- 이를 통해 특정 사용자나 특정 요청의 로그만 필터링할 수 있다.
- 스레드 풀 재사용 시 이전 요청의 MDC가 오염되므로 반드시 finally에서 MDC.clear()를 호출해야 한다.

### Q6. "Polling Publisher 방식의 관찰성은 어떻게 설계하나요?"
- 폴링 주기, 처리 건수, 실패 건수를 Micrometer 카운터로 기록한다.
- Outbox 미발행 이벤트의 누적 건수를 Gauge로 추적하여 처리 지연 알람을 설정한다.
- 각 이벤트 발행 성공/실패를 로그로 기록하고 TraceId를 이벤트에 포함시켜 추적성을 확보한다.
