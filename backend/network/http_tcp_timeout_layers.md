# HTTP/TCP 프로토콜과 4계층 타임아웃 설계

## 1. TCP 3-way Handshake

TCP는 연결 지향 프로토콜로, 데이터 전송 전에 반드시 연결을 수립한다.

### 연결 수립 과정

```
클라이언트                    서버
    |   ── SYN (seq=x) ──>    |   클라이언트: "연결하고 싶어요"
    |                         |
    |  <── SYN-ACK ──────     |   서버: "OK, 나도 연결할게요 (seq=y, ack=x+1)"
    |   (seq=y, ack=x+1)      |
    |                         |
    |   ── ACK (ack=y+1) ──>  |   클라이언트: "확인했어요"
    |                         |
    [연결 수립 완료 — 데이터 전송 시작]
```

### 단계별 오류 시나리오

| 단계 | 오류 원인 | 결과 |
|---|---|---|
| SYN 전송 후 응답 없음 | 서버 다운, 방화벽 차단, 잘못된 포트 | connect timeout 발생 |
| SYN-ACK 받았으나 ACK 유실 | 네트워크 불안정 | 서버는 SYN_RCVD 상태로 대기, 재전송 발생 |
| 연결 수립 후 데이터 응답 없음 | 서버 과부하, 로직 블로킹 | read timeout 발생 |

**Connect Timeout**: SYN을 보내고 SYN-ACK를 받을 때까지의 대기시간 제한
**Read Timeout**: 연결 수립 후 응답 데이터를 받을 때까지의 대기시간 제한

---

## 2. TLS Handshake (HTTPS)

HTTPS는 TCP 연결 수립 후 TLS Handshake를 추가로 수행한다.

### TLS 1.2 Handshake

```
클라이언트                         서버
    |                               |
    |  ── ClientHello ───────────>  |  지원 암호화 목록, TLS 버전, 랜덤값
    |  <── ServerHello ──────────   |  선택된 암호화 방식, 서버 랜덤값
    |  <── Certificate ──────────   |  서버 인증서 (공개키 포함)
    |  <── ServerHelloDone ──────   |
    |                               |
    |  [인증서 검증: CA 체인 확인]    |
    |                               |
    |  ── ClientKeyExchange ─────>  |  Pre-Master Secret (서버 공개키로 암호화)
    |  ── ChangeCipherSpec ──────>  |  "이후부터 암호화 통신"
    |  ── Finished ──────────────>  |
    |  <── ChangeCipherSpec ──────  |
    |  <── Finished ─────────────  |
    |                               |
    [암호화 통신 시작]
    총 2-RTT 소요
```

### TLS 1.3 개선사항

```
TLS 1.3:
    |  ── ClientHello ───────────>  |  (지원 key share 포함)
    |  <── ServerHello + Finished    |  (1-RTT!)
    |  ── Finished ──────────────>  |
    [암호화 통신 시작]
    총 1-RTT 소요 (1.2 대비 1회 왕복 단축)

    0-RTT (Session Resumption):
    이전 세션 키를 재사용하여 첫 요청과 함께 데이터 전송 가능
    (단, 재전송 공격 취약점 주의)
```

| 항목 | TLS 1.2 | TLS 1.3 |
|---|---|---|
| Handshake RTT | 2-RTT | 1-RTT |
| Session Resumption | Session ID / Session Ticket | 0-RTT PSK |
| 지원 암호화 | RSA, DHE 등 다수 | ECDHE 기반만 (Forward Secrecy 강제) |
| 취약한 알고리즘 | RC4, MD5 허용 가능 | 완전 제거 |

---

## 3. HTTP Keep-alive / Connection Reuse

### 3.1 HTTP/1.1 Persistent Connection

HTTP/1.0에서는 요청마다 TCP 연결을 새로 맺고 끊었다 (3-way handshake 반복).
HTTP/1.1부터는 기본적으로 **persistent connection**을 사용한다.

```
HTTP/1.0:
  [TCP 연결] → 요청1/응답1 → [TCP 종료] → [TCP 연결] → 요청2/응답2 → [TCP 종료]

HTTP/1.1 (Keep-alive):
  [TCP 연결] → 요청1/응답1 → 요청2/응답2 → 요청3/응답3 → ... → [TCP 종료]
```

```
요청 헤더: Connection: keep-alive
응답 헤더: Connection: keep-alive
           Keep-Alive: timeout=60, max=100
```

- `timeout=60`: 60초 동안 요청이 없으면 서버가 연결 종료
- `max=100`: 이 연결에서 최대 100번 요청 후 종료

### 3.2 Connection Pool과의 차이

| 항목 | HTTP Keep-alive | Connection Pool (HikariCP 등) |
|---|---|---|
| 목적 | HTTP 요청 간 TCP 연결 재사용 | DB/외부 서비스 연결을 미리 준비 |
| 관리 주체 | HTTP 클라이언트 (HttpClient, WebClient) | 풀 라이브러리 (HikariCP, Apache HttpClient) |
| 연결 생성 | 첫 요청 시 | 애플리케이션 시작 시 미리 생성 |
| 주요 설정 | timeout, max | maximumPoolSize, minimumIdle, connectionTimeout |

---

## 4. HTTP 버전 비교

### HTTP/1.1 — Head-of-Line Blocking

```
요청1 ──────────────────> [처리중...] ─────────────────> 응답1
                    요청2가 응답1을 기다리며 블로킹됨
```

하나의 TCP 연결에서 요청-응답이 순서대로 처리되어야 하므로, 앞 요청이 느리면 뒤 요청이 기다린다.
이를 피하려면 여러 TCP 연결을 동시에 열어야 한다 (브라우저는 도메인당 6개).

### HTTP/2 — 멀티플렉싱

```
TCP 연결 하나에서:
스트림1: 요청1 ──> ... ──> 응답1
스트림2:    요청2 ──> ...────────────> 응답2
스트림3:         요청3 ──> 응답3
(모두 동시에 진행, 순서 무관)
```

| 기능 | HTTP/1.1 | HTTP/2 |
|---|---|---|
| 멀티플렉싱 | 없음 (HOL Blocking) | 있음 (스트림 기반) |
| 헤더 압축 | 없음 (텍스트) | HPACK (이진 + 허프만 인코딩) |
| 서버 푸시 | 없음 | 있음 (클라이언트 요청 없이 리소스 푸시) |
| 프로토콜 | 텍스트 | 이진 |
| 연결 수 | 여러 개 | 하나로 충분 |

**TCP 레벨 HOL Blocking**: HTTP/2도 TCP 레벨에서는 패킷 손실 시 모든 스트림이 블로킹된다.

### HTTP/3 — QUIC (UDP 기반)

HTTP/3는 TCP 대신 QUIC 프로토콜을 사용한다.

```
HTTP/2:  TCP 레벨 HOL Blocking 존재
         패킷 손실 → 해당 TCP 연결의 모든 스트림 블로킹

HTTP/3 (QUIC):
         UDP 기반으로 스트림별 독립적인 흐름 제어
         패킷 손실 → 해당 스트림만 재전송, 다른 스트림 영향 없음
```

| 항목 | HTTP/2 | HTTP/3 |
|---|---|---|
| 전송 프로토콜 | TCP | QUIC (UDP 기반) |
| HOL Blocking | TCP 레벨 있음 | 없음 |
| 연결 수립 | TCP + TLS (1-2 RTT) | 0-1 RTT (QUIC 내장 TLS 1.3) |
| 적합한 환경 | 안정적인 유선 네트워크 | 모바일, 불안정한 네트워크 |

---

## 5. 4계층 타임아웃 설계

타임아웃은 하나의 계층만 설정해서는 안 된다.
각 계층이 서로 맞물려야 의미 있는 타임아웃이 된다.

### 5.1 계층 구조

```
┌───────────────────────────────────────────┐
│  OS 레벨 (커널)                             │
│  SO_TIMEOUT, TCP_KEEPALIVE               │
├───────────────────────────────────────────┤
│  프록시 / L7 로드밸런서                      │
│  Nginx: proxy_read_timeout               │
│  ALB: idle timeout                       │
├───────────────────────────────────────────┤
│  애플리케이션                               │
│  WebClient / RestTemplate / HttpClient   │
│  connect-timeout, read-timeout           │
├───────────────────────────────────────────┤
│  DB                                       │
│  statement timeout, lock_wait_timeout    │
│  query timeout                           │
└───────────────────────────────────────────┘
```

### 5.2 각 계층 설명

#### OS 레벨

```bash
# TCP_KEEPALIVE: 유휴 연결이 살아있는지 확인
# net.ipv4.tcp_keepalive_time = 7200 (2시간) — 실무에서는 60~120초 권장
# net.ipv4.tcp_keepalive_intvl = 75 (재시도 간격)
# net.ipv4.tcp_keepalive_probes = 9 (최대 재시도 횟수)

sysctl net.ipv4.tcp_keepalive_time
```

#### 프록시 / L7 로드밸런서

```nginx
# Nginx 설정
upstream wallet_service {
    server wallet-service:8080;
    keepalive 32;  # upstream 연결 풀 크기
}

server {
    location /api/ {
        proxy_connect_timeout    5s;   # upstream 연결 수립 최대 대기
        proxy_read_timeout      30s;   # upstream 응답 최대 대기
        proxy_send_timeout      10s;   # upstream에 요청 전송 최대 대기
    }
}
```

AWS ALB의 기본 idle timeout은 60초다. 이보다 긴 요청(대용량 업로드 등)은 타임아웃을 늘려야 한다.

#### 애플리케이션 — WebClient (Spring WebFlux)

```kotlin
// WebClient 타임아웃 설정
@Configuration
class WebClientConfig {
    @Bean
    fun externalPaymentWebClient(): WebClient {
        val httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3_000)  // 연결 수립 3초
            .responseTimeout(Duration.ofSeconds(10))               // 응답 수신 10초
            .doOnConnected { conn ->
                conn.addHandlerLast(ReadTimeoutHandler(10, TimeUnit.SECONDS))
                conn.addHandlerLast(WriteTimeoutHandler(5, TimeUnit.SECONDS))
            }

        return WebClient.builder()
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .baseUrl("https://api.external-payment.com")
            .build()
    }
}
```

#### 애플리케이션 — RestTemplate (Spring MVC)

```kotlin
@Bean
fun restTemplate(): RestTemplate {
    val factory = HttpComponentsClientHttpRequestFactory()
    factory.setConnectTimeout(3_000)   // 연결 수립 3초
    factory.setReadTimeout(10_000)     // 응답 수신 10초
    return RestTemplate(factory)
}
```

#### DB 타임아웃

```yaml
# application.yml - PostgreSQL + HikariCP
spring:
  datasource:
    hikari:
      connection-timeout: 3000       # 커넥션 풀에서 연결 획득 대기시간 (3초)
      idle-timeout: 600000           # 유휴 연결 유지시간 (10분)
      max-lifetime: 1800000          # 연결 최대 수명 (30분)
      maximum-pool-size: 20
  jpa:
    properties:
      hibernate:
        jdbc:
          # statement timeout: 단일 쿼리 최대 실행시간 (5초)
          # PostgreSQL은 statement_timeout 파라미터로도 설정 가능
```

```sql
-- PostgreSQL: 세션 레벨 쿼리 타임아웃
SET statement_timeout = '5000';   -- 5초 초과 쿼리 강제 종료

-- lock_timeout: 락 대기 최대시간
SET lock_timeout = '3000';        -- 3초 이상 락 대기 시 오류 반환
```

### 5.3 타임아웃 캐스케이드 문제

**핵심 원칙: 내부 타임아웃 < 외부(상위) 타임아웃**

```
잘못된 설정:
  Nginx read_timeout = 10초
  앱 WebClient read_timeout = 30초 ← 앱이 더 길면 의미 없음!

  이유: Nginx가 10초에 502 반환 → 앱은 30초까지 기다리고 싶지만
        클라이언트는 이미 502를 받았음 → 앱의 타임아웃 설정이 무의미

올바른 설정:
  클라이언트 타임아웃:     35초
  Nginx read_timeout:     30초
  앱 WebClient timeout:   25초  ← 앱이 가장 짧아야
  외부 API timeout:       20초
  DB statement timeout:   5초
```

계층별 타임아웃 권장 관계:
```
DB timeout < 앱 timeout < 프록시 timeout < 클라이언트 timeout
```

---

## 6. 실무 예시: 외부 결제 API 연동 타임아웃 전략

```kotlin
// 외부 결제사 API 연동 타임아웃 설계
@Configuration
class PaymentApiConfig {

    // 타임아웃 전략:
    // 결제사 API SLA: 응답 보장 15초
    // 앱 WebClient: 12초 (SLA보다 짧게, 여유 확보)
    // Nginx: 20초 (앱보다 길게)
    // 클라이언트 전체 타임아웃: 25초

    @Bean
    fun paymentApiWebClient(): WebClient {
        val httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3_000)
            .responseTimeout(Duration.ofSeconds(12))
            .doOnConnected { conn ->
                conn.addHandlerLast(ReadTimeoutHandler(12, TimeUnit.SECONDS))
            }

        return WebClient.builder()
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .baseUrl(paymentApiBaseUrl)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build()
    }
}

// 타임아웃 + 재시도 + 서킷브레이커 조합
@Service
class ExternalPaymentService(
    private val paymentApiWebClient: WebClient,
    private val circuitBreaker: CircuitBreaker
) {
    fun requestPayout(request: PayoutRequest): PayoutResponse {
        return circuitBreaker.executeSupplier {
            paymentApiWebClient.post()
                .uri("/v1/payout")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(PayoutResponse::class.java)
                .timeout(Duration.ofSeconds(12))  // 추가 안전장치
                .retryWhen(Retry.backoff(2, Duration.ofMillis(500))
                    .filter { it is TimeoutException || it is ConnectException })
                .block()
                ?: throw ExternalPaymentException("응답 없음")
        }
    }
}
```

---

## 7. 타임아웃 값 설정 기준

| 구분 | 권장 범위 | 고려 사항 |
|---|---|---|
| DB 쿼리 (단순 조회) | 1~3초 | 인덱스 튜닝 후 정상 수행시간 기준 |
| DB 쿼리 (복잡한 집계) | 5~30초 | 배치/리포트 쿼리는 별도 설정 |
| 내부 서비스 API | 1~5초 | MSA 내부 통신은 빠르게 |
| 외부 결제 API | 10~30초 | 외부 SLA 확인 필수 |
| Kafka 메시지 발행 | 5~10초 | 프로듀서 타임아웃 |
| 연결 수립 (connect) | 1~5초 | 네트워크 지연 고려 |

---

## 면접 포인트

### Q1. "connect timeout과 read timeout의 차이는?"
- connect timeout: TCP 3-way handshake + TLS handshake가 완료될 때까지의 최대 대기시간. 서버에 연결조차 안 될 때 적용된다.
- read timeout: 연결이 수립된 후 서버로부터 응답 데이터를 받을 때까지의 최대 대기시간. 서버 처리가 느릴 때 적용된다.
- connect timeout은 수 초(1~5초), read timeout은 비즈니스 요구사항에 따라 더 길게 설정하는 것이 일반적이다.

### Q2. "HTTP/1.1의 Head-of-Line Blocking이 무엇인가요?"
- HTTP/1.1에서 하나의 TCP 연결은 요청-응답을 순서대로 처리해야 한다.
- 앞의 요청 처리가 느리면 그 뒤에 대기 중인 요청들이 모두 블로킹된다.
- HTTP/2는 멀티플렉싱으로 이를 해결한다. 하나의 TCP 연결에서 여러 스트림을 동시에 처리한다.
- 단, HTTP/2도 TCP 레벨에서는 패킷 손실 시 HOL Blocking이 발생하며, HTTP/3(QUIC)가 이를 완전히 해결한다.

### Q3. "타임아웃 캐스케이드 문제가 무엇인가요?"
- 상위 계층(Nginx, 클라이언트) 타임아웃이 하위 계층(앱, DB) 타임아웃보다 짧으면, 하위 계층의 타임아웃 설정이 의미 없어진다.
- 예: Nginx가 10초에 502를 반환하면 앱이 30초를 기다리도록 설정해도 클라이언트는 이미 오류를 받은 상태다.
- 원칙: DB timeout < 앱 timeout < 프록시 timeout < 클라이언트 timeout 순서가 되어야 한다.

### Q4. "TLS 1.2와 1.3의 주요 차이는?"
- TLS 1.2는 2-RTT Handshake, TLS 1.3은 1-RTT Handshake로 연결 수립 시간이 단축된다.
- TLS 1.3은 Session Resumption 시 0-RTT 데이터 전송이 가능하다 (재전송 공격 주의).
- TLS 1.3은 취약한 알고리즘(RC4, MD5 등)을 완전히 제거하고 Forward Secrecy를 강제한다.

### Q5. "HikariCP connection-timeout 설정이 왜 중요한가요?"
- connection-timeout은 HikariCP 풀에서 유휴 커넥션이 없을 때 커넥션 획득을 기다리는 최대시간이다.
- 이 값이 너무 크면 DB 부하 시 스레드가 오래 대기하여 서비스 장애로 이어질 수 있다.
- 이 값이 너무 작으면 트래픽 급증 시 정상 요청도 타임아웃이 발생한다.
- 실무에서는 3~5초를 권장하며, 타임아웃 시 즉시 오류 반환으로 빠른 실패(Fail-Fast) 패턴을 구현한다.

### Q6. "외부 결제사 API 연동 시 타임아웃을 어떻게 설정하나요?"
- 먼저 외부 결제사의 SLA 응답시간을 확인한다.
- 앱의 WebClient timeout을 SLA보다 약간 짧게 설정한다 (예: SLA 15초 → WebClient 12초).
- Nginx proxy_read_timeout은 앱 timeout보다 길게 설정한다 (예: 20초).
- 타임아웃 발생 시 재시도 횟수와 지연 전략(Exponential Backoff)을 함께 설계한다.
- 재시도 안전성을 위해 멱등성 키(idempotency key)를 요청에 포함한다.
- 서킷브레이커(Resilience4j)와 조합하여 지속적인 타임아웃 시 빠른 실패(open circuit)를 적용한다.
