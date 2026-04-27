# Spring 테스트에서 시간 제어 — Clock 추상화

## 1. 문제 배경: 시간 직접 호출의 한계

### 1.1 흔한 코드 패턴

```java
// 안티패턴: 시간을 직접 호출
public class TokenService {
    public boolean isExpired(Token token) {
        return token.getExpiresAt().isBefore(LocalDateTime.now()); // 테스트 불가!
    }

    public Token issue(long userId) {
        LocalDateTime now = LocalDateTime.now(); // 현재 시각 고정 불가
        return new Token(userId, now, now.plusHours(1));
    }
}
```

### 1.2 발생하는 문제

**Flaky 테스트**: 같은 테스트가 실행 시각에 따라 성공/실패가 달라진다.

```java
@Test
void 만료된_토큰은_유효하지_않다() {
    // 2024-01-01 23:59:59에 만료 토큰 생성 → 자정 이후 실행 시 이미 만료됨
    // → 테스트 시각에 따라 결과가 달라지는 Flaky 테스트
    Token token = new Token(userId, LocalDateTime.of(2024, 1, 1, 0, 0), LocalDateTime.of(2024, 1, 1, 23, 59, 59));
    assertThat(tokenService.isExpired(token)).isTrue(); // 실행 시각에 따라 달라짐
}
```

**시간 의존 로직 예시**

| 시나리오 | 직접 호출의 문제 |
|---|---|
| 토큰/세션 만료 체크 | 만료 경계 테스트 불가 |
| TTL 계산 (캐시, OTP) | 만료 직전/직후 상태 검증 불가 |
| 타임스탬프 저장 | 저장된 값이 예측 불가 |
| 배치 처리 기준 시각 | 특정 날짜 시나리오 재현 불가 |
| 스케줄링 조건 판단 | 영업일/비영업일 분기 테스트 불가 |

---

## 2. java.time.Clock 개념

`java.time.Clock`은 Java 8에서 도입된 시간 소스 추상화 클래스다. `LocalDateTime.now()`, `Instant.now()` 등 모든 `java.time` 클래스가 `Clock`을 인자로 받을 수 있다.

### 2.1 Clock 팩토리 메서드

**`Clock.systemDefaultZone()`** — 운영 환경용, 시스템 기본 시간대 기준 실제 시계

```java
Clock clock = Clock.systemDefaultZone();
LocalDateTime now = LocalDateTime.now(clock); // 실제 현재 시각
```

**`Clock.fixed(Instant, ZoneId)`** — 테스트용, 항상 동일한 시각 반환

```java
Clock fixedClock = Clock.fixed(
    Instant.parse("2024-01-01T00:00:00Z"),
    ZoneOffset.UTC
);
LocalDateTime now = LocalDateTime.now(fixedClock); // 항상 2024-01-01T00:00:00
```

**`Clock.offset(Clock, Duration)`** — 기준 시계에서 특정 시간만큼 앞당기거나 미루기

```java
Clock tomorrowClock = Clock.offset(Clock.systemDefaultZone(), Duration.ofDays(1));
LocalDateTime tomorrow = LocalDateTime.now(tomorrowClock); // 내일 시각
```

**`Clock.tick(Clock, Duration)`** — 지정한 틱 단위로 시간이 진행 (초 단위, 분 단위 등)

```java
Clock minuteClock = Clock.tick(Clock.systemDefaultZone(), Duration.ofMinutes(1));
// 초/나노초 부분이 0으로 절사되어 분 단위로만 변경됨
```

### 2.2 Clock과 java.time API 연동

```java
Instant instant       = Instant.now(clock);
LocalDateTime ldt     = LocalDateTime.now(clock);
LocalDate ld          = LocalDate.now(clock);
ZonedDateTime zdt     = ZonedDateTime.now(clock);
OffsetDateTime odt    = OffsetDateTime.now(clock);
```

---

## 3. 코드 변경 방법: Clock 주입

### 3.1 Before / After

```java
// Before: 시간 직접 호출
public class OtpService {
    public Otp generate(String target) {
        LocalDateTime now = LocalDateTime.now();          // 직접 호출
        LocalDateTime expiredAt = now.plusMinutes(3);
        return new Otp(target, now, expiredAt);
    }
}
```

```java
// After: Clock 주입
@RequiredArgsConstructor
public class OtpService {
    private final Clock clock;                            // 주입받은 Clock 사용

    public Otp generate(String target) {
        LocalDateTime now = LocalDateTime.now(clock);     // Clock 경유
        LocalDateTime expiredAt = now.plusMinutes(3);
        return new Otp(target, now, expiredAt);
    }

    public boolean isValid(Otp otp) {
        return LocalDateTime.now(clock).isBefore(otp.getExpiredAt());
    }
}
```

### 3.2 Instant 기반 코드

```java
@RequiredArgsConstructor
public class SessionService {
    private final Clock clock;

    public Session create(long userId) {
        Instant now = Instant.now(clock);
        Instant expiry = now.plus(Duration.ofHours(2));
        return new Session(userId, now, expiry);
    }
}
```

---

## 4. Spring Bean으로 Clock 등록

운영 환경에서는 실제 시계를, 테스트 환경에서는 고정 시계를 주입할 수 있도록 Bean으로 등록한다.

```java
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
```

이후 서비스에서 `@Autowired` 또는 생성자 주입으로 받는다.

```java
@Service
@RequiredArgsConstructor
public class TokenService {
    private final TokenRepository tokenRepository;
    private final Clock clock;

    public Token issue(long userId) {
        LocalDateTime now = LocalDateTime.now(clock);
        Token token = Token.builder()
            .userId(userId)
            .issuedAt(now)
            .expiresAt(now.plusHours(1))
            .build();
        return tokenRepository.save(token);
    }

    public boolean isExpired(Token token) {
        return LocalDateTime.now(clock).isAfter(token.getExpiresAt());
    }
}
```

---

## 5. 테스트에서 Clock.fixed() 활용

### 5.1 단위 테스트 — Clock.fixed() 직접 주입

```java
@Test
void 만료_1초_전에는_유효하다() {
    // given: 2024-01-01T01:00:00 기준 고정 시계
    Clock fixedClock = Clock.fixed(
        Instant.parse("2024-01-01T00:59:59Z"), ZoneOffset.UTC
    );
    TokenService service = new TokenService(tokenRepository, fixedClock);

    // 만료 시각: 2024-01-01T01:00:00
    Token token = Token.builder()
        .expiresAt(LocalDateTime.of(2024, 1, 1, 1, 0, 0))
        .build();

    // when & then
    assertThat(service.isExpired(token)).isFalse();
}

@Test
void 만료_후에는_유효하지_않다() {
    Clock fixedClock = Clock.fixed(
        Instant.parse("2024-01-01T01:00:01Z"), ZoneOffset.UTC
    );
    TokenService service = new TokenService(tokenRepository, fixedClock);

    Token token = Token.builder()
        .expiresAt(LocalDateTime.of(2024, 1, 1, 1, 0, 0))
        .build();

    assertThat(service.isExpired(token)).isTrue();
}
```

### 5.2 Spring Boot 테스트 — @MockBean Clock

```java
@SpringBootTest
class TokenServiceIntegrationTest {

    @Autowired
    private TokenService tokenService;

    @MockBean
    private Clock clock;

    @BeforeEach
    void setup() {
        Instant fixedInstant = Instant.parse("2024-01-01T00:00:00Z");
        given(clock.instant()).willReturn(fixedInstant);
        given(clock.getZone()).willReturn(ZoneOffset.UTC);
    }

    @Test
    void 발급된_토큰의_만료시각은_1시간_후다() {
        Token token = tokenService.issue(1L);

        assertThat(token.getExpiresAt())
            .isEqualTo(LocalDateTime.of(2024, 1, 1, 1, 0, 0));
    }
}
```

> **`Clock.fixed()` vs `@MockBean Clock` 비교**
>
> | 구분 | Clock.fixed() | @MockBean Clock |
> |---|---|---|
> | 사용 계층 | 단위 테스트 (Spring 컨텍스트 불필요) | Spring Boot 통합 테스트 |
> | 설정 방법 | 생성자에 직접 전달 | given() 스터빙 |
> | 컨텍스트 로딩 | 불필요 (빠름) | 필요 (느림) |
> | 적합 상황 | 서비스 로직 집중 검증 | 전체 빈 연동 검증 |

### 5.3 시간 이동 테스트 — Clock.offset() 활용

```java
@Test
void TTL_만료_후_재발급_가능하다() {
    Clock issuedClock = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC);
    OtpService service = new OtpService(issuedClock);
    Otp otp = service.generate("010-1234-5678");

    // 3분 1초 후 시점으로 이동
    Clock expiredClock = Clock.offset(issuedClock, Duration.ofMinutes(3).plusSeconds(1));
    OtpService expiredService = new OtpService(expiredClock);

    assertThat(expiredService.isValid(otp)).isFalse();
}
```

---

## 6. Record 타입에서의 활용 (Java 16+)

불변 도메인 객체에서는 `Clock`을 정적 팩토리 메서드로 주입하는 패턴이 깔끔하다.

```java
public record TransferRecord(
    long id,
    BigDecimal amount,
    OffsetDateTime requestedAt,
    OffsetDateTime expiredAt
) {
    private static final Duration TTL = Duration.ofMinutes(10);

    // 팩토리 메서드에서 Clock을 받아 현재 시각 계산
    public static TransferRecord of(long id, BigDecimal amount, Clock clock) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        return new TransferRecord(id, amount, now, now.plus(TTL));
    }

    public boolean isExpired(Clock clock) {
        return OffsetDateTime.now(clock).isAfter(expiredAt);
    }
}
```

```java
@Test
void 이체_요청은_10분_후_만료된다() {
    Clock issuedClock = Clock.fixed(Instant.parse("2024-06-01T09:00:00Z"), ZoneOffset.UTC);
    TransferRecord record = TransferRecord.of(1L, BigDecimal.valueOf(100_000), issuedClock);

    Clock after10min = Clock.offset(issuedClock, Duration.ofMinutes(10).plusSeconds(1));
    assertThat(record.isExpired(after10min)).isTrue();

    Clock before10min = Clock.offset(issuedClock, Duration.ofMinutes(9));
    assertThat(record.isExpired(before10min)).isFalse();
}
```

**장점**: Record는 불변이므로 생성자에서 시간을 고정하되, `Clock`만 교체하면 어떤 시점이든 재현 가능하다.

---

## 7. 실무 적용 패턴 요약

### 7.1 다중 서비스가 Clock을 공유하는 경우

```java
@Configuration
public class ClockConfig {

    // 운영: 실제 시계
    @Bean
    @Profile("!test")
    public Clock clock() {
        return Clock.systemDefaultZone();
    }

    // 테스트 프로파일: 고정 시계 (필요 시)
    @Bean
    @Profile("test")
    public Clock testClock() {
        return Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC);
    }
}
```

> 주의: 여러 서비스가 같은 `Clock` 빈을 공유할 때, 한 서비스에서 시각을 변경하면 다른 서비스에도 영향을 준다.
> 통합 테스트에서는 `@MockBean Clock`으로 각 테스트마다 독립적으로 스터빙하는 방식이 안전하다.

### 7.2 헬퍼 유틸 클래스 패턴

공통 시간 유틸을 운영/테스트 환경 분리 없이 사용할 때:

```java
@Component
@RequiredArgsConstructor
public class TimeProvider {
    private final Clock clock;

    public LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    public Instant instant() {
        return Instant.now(clock);
    }

    public OffsetDateTime offsetNow() {
        return OffsetDateTime.now(clock);
    }
}
```

```java
// 사용 측
@RequiredArgsConstructor
public class NotificationService {
    private final TimeProvider timeProvider;

    public Notification create(String message) {
        return new Notification(message, timeProvider.now());
    }
}
```

---

## 면접 포인트

### Q1. "LocalDateTime.now()를 직접 호출하면 테스트에서 어떤 문제가 발생하는가?"

- 테스트 실행 시각에 따라 결과가 달라지는 **Flaky 테스트**가 된다.
- 예: 만료 시간 체크 로직을 테스트할 때, 만료 경계값(직전/직후) 시나리오를 재현할 수 없다.
- `LocalDateTime.now()`는 JVM 내에서 교체하기 어려워 Mockito로도 모킹이 불가하다.
- 결국 시간 의존 로직이 늘어날수록 테스트 커버리지가 실질적으로 낮아진다.

### Q2. "Clock.fixed()와 @MockBean Clock의 차이는 무엇인가?"

- `Clock.fixed()`는 스프링 컨텍스트 없이 단위 테스트에서 생성자 주입으로 직접 사용한다. 빠르고 단순하며, 특정 서비스 로직에 집중한 검증에 적합하다.
- `@MockBean Clock`은 스프링 통합 테스트(`@SpringBootTest`)에서 빈을 교체할 때 사용한다. `given(clock.instant()).willReturn(...)` 형태로 스터빙하며, `@BeforeEach`로 매 테스트마다 독립된 시각을 설정할 수 있다.
- `@MockBean`은 스프링 컨텍스트를 재로딩하지 않으면서 빈을 교체하므로 통합 테스트 속도에 유리하지만, `instant()`와 `getZone()` 두 메서드를 모두 스터빙해야 `LocalDateTime.now(clock)` 등이 정상 동작한다.

### Q3. "여러 서비스가 Clock을 공유할 때 주의사항은?"

- 싱글턴 빈으로 등록된 `Clock.fixed()`는 모든 테스트에 동일한 시각을 반환한다. 테스트 간 시각이 달라야 할 경우 상태를 공유하므로 간섭이 발생할 수 있다.
- `@MockBean Clock`을 사용하면 `@BeforeEach`에서 매번 스터빙을 재설정해 테스트 독립성을 보장할 수 있다.
- 여러 서비스가 같은 `Clock` 빈을 쓸 때, 한 테스트에서 시각을 변경하면 다른 서비스 로직의 시각도 함께 바뀐다는 점을 항상 인식해야 한다.

### Q4. "Clock 추상화 외에 시간을 제어하는 다른 방법은 없는가?"

- `Mockito.mockStatic(LocalDateTime.class)`로 정적 메서드를 모킹할 수 있지만, 모킹 범위가 스레드 단위이고 `try-with-resources`로 관리해야 하며 누수 위험이 있다.
- `PowerMock`으로 정적 메서드를 교체하는 방법도 있으나 유지보수가 어렵고 Spring과의 호환성 문제가 생긴다.
- 결론적으로 **Clock 추상화가 가장 표준적이고 안전한 방법**이다. Spring 공식 문서, 마틴 파울러의 "Clock" 패턴 등 커뮤니티 표준으로 자리잡았다.

### Q5. "OffsetDateTime과 LocalDateTime 중 어떤 타입과 함께 Clock을 사용하는 것이 좋은가?"

- **서버 간 통신, DB 저장, API 응답**에는 `OffsetDateTime`(또는 `Instant`)을 권장한다. 타임존 정보가 포함되어 있어 서버가 다른 지역에 배포되더라도 동일한 시각을 보장한다.
- **로컬 날짜/시간 표시(UI, 보고서)** 에는 `LocalDateTime`을 사용한다.
- `Clock.fixed(Instant, ZoneId)`로 두 타입 모두 동일하게 제어할 수 있으므로, 타입 선택은 도메인 요구사항을 따르고 Clock 추상화를 병행하면 된다.
