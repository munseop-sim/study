# 테스팅 (Testing)

## TDD (Test Driven Development) - 테스트 주도 개발

**테스트 주도 개발(Test Driven Development)** 은 매우 짧은 개발 사이클을 반복하는 소프트웨어 개발 프로세스입니다. 개발자는 먼저 요구사항을 검증하는 자동화된 테스트 케이스를 작성합니다. 그 이후에는 테스트 케이스를 통과하기 위한 최소한의 코드를 생성하고, 작성한 코드를 리팩토링하는 과정을 반복합니다.

### Red-Green-Refactor 사이클

```
Red   → 실패하는 테스트 케이스 작성 (아직 구현이 없으므로 실패)
Green → 테스트를 통과하기 위한 최소한의 코드 작성
Refactor → 중복 제거 및 코드 품질 개선 (테스트는 여전히 통과해야 함)
```

### TDD 사이클에서 의식해야 할 부분들

- 일단 간단하고, 해보기 쉬운 것을 먼저 시도합니다.
- 실패하는 테스트를 통과하기 위해서는 최소한의 코드를 작성해야 합니다.
- 테스트를 점점 구체화할수록 프로덕션 코드는 점점 범용적으로 됩니다. (커버 가능한 케이스가 점점 많아집니다.)
- 실패하는 테스트가 있을 때만 프로덕션 코드를 작성합니다.
- 실패를 나타내는 데 충분한 정도의 테스트만 작성합니다.

### TDD의 효과

위와 같은 부분들을 의식하면서 TDD 사이클을 반복하다 보면:
- 작성한 코드가 가지는 불안정성을 개선하여 생산성을 높일 수 있습니다.
- 테스트 가능하며 결합이 느슨한 시스템을 점진적으로 만들어 나갈 수 있습니다.
- 하지만, TDD가 오히려 비효율적인 경우도 존재하기 때문에 다른 모든 기술과 마찬가지로 비판적으로 사고하는 것도 중요합니다.

참고:
- [maeil-mail: 테스트 주도 개발이 무엇인가요?](https://www.maeil-mail.kr/question/122)

---

## 단위 테스트 vs 통합 테스트

### 단위 테스트 (Unit Test)

소프트웨어의 가장 작은 단위, 즉 개별 메서드나 함수의 기능을 검증하는 테스트입니다.

- 특정 기능이 올바르게 동작하는지 확인하기 위함입니다.
- 독립적이고 빠르게 실행됩니다.
- 외부 의존성(DB, 네트워크 등)을 Mock으로 대체합니다.

### 통합 테스트 (Integration Test)

개별 모듈들이 결합되어 전체 시스템이 올바르게 동작하는지 검증하는 테스트입니다.

- 모듈 간의 상호작용이 올바르게 동작하는지 확인하기 위함입니다.
- 실제 데이터베이스, 네트워크 등의 외부 시스템과의 통합을 테스트합니다.

### 슬라이스 테스트 (Slice Test)

특정 레이어(ex. controller, service, repository)에 대한 테스트입니다.

- 애플리케이션의 특정 슬라이스가 올바르게 동작하는지 확인하기 위해 작성됩니다.
- 스프링의 특정 컴포넌트만 로드하여 테스트하므로 상대적으로 빠르게 실행됩니다.
- 관련 어노테이션: `@WebMvcTest`, `@DataJpaTest` 등

### 테스트 코드를 작성해야 하는 이유

- 버그를 조기에 발견할 수 있습니다.
- 리팩터링을 수행할 경우 유용합니다.
- 개발 속도를 향상시킬 수 있습니다.
- 코드에 대한 문서로서 역할을 수행할 수 있습니다.

참고:
- [maeil-mail: 단위 테스트와 통합 테스트의 차이점은 무엇인가요?](https://www.maeil-mail.kr/question/128)
- [Spring Boot 슬라이스 테스트](https://tecoble.techcourse.co.kr/post/2021-05-18-slice-test/)

---

## 코드 커버리지 (Code Coverage)

테스트 케이스들이 프로덕션 코드를 실행한 정도를 나타낸 것을 **코드 커버리지(Code Coverage)** 라고 합니다.

### 커버리지 종류

코드 커버리지는 측정하는 기준에 따라서 크게 3가지로 나뉩니다.

#### 1. 구문 커버리지 (Statement Coverage) = 라인 커버리지

단순히 프로덕션 코드의 라인이 실행된 것을 확인합니다.

- 예: 5줄의 코드를 포함하는 A 메서드를 테스트했는데, 5줄 모두 실행된 경우 구문 커버리지는 100%

#### 2. 결정 커버리지 (Decision Coverage) = 브랜치 커버리지

프로덕션 코드에 모든 조건식이 참이거나 거짓으로 평가되는 케이스가 최소 한 번씩 실행되는 것을 판단합니다.

```java
public void productionCode(int a, int b) {
    if (a > 0 && b > 0) { // 조건식
        // ...
    }
}
// productionCode(1, 1) -> 조건식 참
// productionCode(0, 1) -> 조건식 거짓
// 위 두 케이스로 결정 커버리지 만족
```

- 코드 내에서 실행 흐름이 분기되는 모든 경로를 테스트하는 것을 목표로 합니다.

#### 3. 조건 커버리지 (Condition Coverage)

메서드 내부의 모든 조건식이 참과 거짓으로 모두 평가되는 것을 의미합니다.

```java
// productionCode(1, 0) -> a>0 참, b>0 거짓
// productionCode(0, 1) -> a>0 거짓, b>0 참
// 위 두 케이스로 조건 커버리지 만족 (but 결정 커버리지 미충족)
```

- 주의: 조건 커버리지를 만족하더라도 결정 커버리지를 만족하지 못할 수 있는 상황이 존재합니다.

### 커버리지가 높다고 무조건 좋을까?

- 커버리지가 높다는 것은 코드의 일부가 테스트에 의해 실행되어 검증되었다는 것을 의미합니다.
- 높은 커버리지는 일반적으로 코드의 안정성과 신뢰성을 높일 수 있지만, 이것이 무조건 항상 좋다고 말할 수는 없습니다.
- 커버리지가 높더라도 테스트 케이스가 부족하거나 부적절하게 작성되었다면 여전히 중요한 버그가 발생할 수 있습니다.
- 모든 코드를 테스트하는 것이 현실적이지 않을 수도 있습니다. 특히 예외 상황이나 경계 조건을 모두 다루기는 어려울 수 있습니다.

참고:
- [maeil-mail: 코드 커버리지에 대해서 설명해주세요](https://www.maeil-mail.kr/question/139)
- [테코블 - 코드 커버리지(Code Coverage)가 뭔가요?](https://tecoble.techcourse.co.kr/post/2020-10-24-code-coverage/)
- [요즘 IT - 개발자여, 테스트 커버리지에 집착 말자](https://yozm.wishket.com/magazine/detail/2471/)

---

## 테스트 더블 (Test Double)

> 출처: https://www.maeil-mail.kr/question/197

### 개념

테스트 코드에서 실제 의존성을 사용하기 어려운 경우, **테스트 더블(Test Double)** 을 사용할 수 있습니다.

실제 의존성을 포함하는 테스트의 문제점:
- 외부 세계에 **부수 효과(Side Effect)** 유발 가능
- 외부 세계에 의존적이기 때문에 **비결정적(Non-deterministic)** 동작 유발
- 실제 의존성 포함을 위한 **복잡한 설정** 필요

테스트 더블은 이를 해결하는 **가짜 의존성**으로:
- 테스트로부터 외부 세계를 보호
- 외부 세계로부터 테스트를 보호
- 복잡한 설정을 단순화

### 테스트 더블의 종류

#### 더미 (Dummy)

아무런 동작도 하지 않으며, **인스턴스화된 객체만 필요**한 경우에 사용됩니다.

```java
// 파라미터 자리만 채우기 위한 용도
UserService userService = new UserService(new DummyEmailService());
```

#### 스텁 (Stub)

구현을 단순한 것으로 대체합니다. 테스트에 맞게 **단순히 원하는 동작을 수행**합니다.

```java
// 항상 특정 값을 반환하도록 설정
when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
```

#### 페이크 (Fake)

제품에는 적합하지 않지만, **실제 동작하는 구현**을 제공합니다.

```java
// 실제 DB 대신 인메모리 구현체 사용
public class InMemoryUserRepository implements UserRepository {
    private final Map<Long, User> store = new HashMap<>();
    // ... 실제 동작하는 구현
}
```

#### 스파이 (Spy)

호출된 내역을 기록합니다. 기록한 내용은 **테스트 결과를 검증**할 때 주로 사용됩니다. 스텁의 일종이기도 합니다.

```java
// 메서드가 몇 번 호출됐는지 검증
verify(emailService, times(1)).sendEmail(any());
```

#### 목 (Mock)

기대한 대로 **상호작용하는지 행위를 검증**합니다. 기대한 것처럼 동작하지 않으면 예외를 발생시킬 수 있습니다. 목 객체는 스텁이자 스파이이기도 합니다.

```java
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {
    @Mock
    private OrderRepository orderRepository;

    @Test
    void 주문_생성_시_저장소에_저장된다() {
        // when
        orderService.createOrder(order);

        // then - 행위 검증
        verify(orderRepository).save(any(Order.class));
    }
}
```

### 종류별 비교

| 종류 | 반환값 | 행위 기록 | 행위 검증 | 실제 동작 |
|---|---|---|---|---|
| 더미 | X | X | X | X |
| 스텁 | O (고정) | X | X | X |
| 페이크 | O (실제 로직) | X | X | O (단순화) |
| 스파이 | O | O | X | △ |
| 목 | O | O | O | X |

### 참고 자료

- [마틴 파울러 - 테스트 더블](https://martinfowler.com/bliki/TestDouble.html)
- [기억보다 기록을 - 테스트 코드에서 내부 구현 검증 피하기](https://jojoldu.tistory.com/614)

---

## 테스트 격리 (Test Isolation)

> 출처: https://www.maeil-mail.kr/question/257

### 개념

**테스트 격리(Test Isolation)** 는 각 테스트가 서로 독립적으로 실행되도록 보장하는 것입니다. 즉, 어떤 테스트가 실행되더라도 다른 테스트의 결과나 상태에 영향을 주거나 받지 않아야 합니다.

### 격리가 중요한 이유

격리가 제대로 이루어지지 않으면 **비결정적 테스트(Non-deterministic Test)** 가 발생합니다.

> **비결정적 테스트**: 같은 테스트를 여러 번 실행했을 때 항상 같은 결과를 내지 않는 테스트

예를 들어, 테스트가 DB와 같은 **공유 자원**에 의존할 경우 실행 순서나 다른 테스트의 실행 여부에 따라 성공/실패가 달라질 수 있습니다.

### Spring에서의 DB 테스트 격리 방법

#### 1. @DirtiesContext

```java
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@SpringBootTest
class MyIntegrationTest {
    // ...
}
```

- 테스트마다 새로운 애플리케이션 컨텍스트를 로드하여 완전한 격리 보장
- **단점**: 컨텍스트를 매번 새로 로드 → 비용이 크고 테스트 속도 느림

#### 2. @Sql (TRUNCATE)

```java
@Sql("/truncate.sql")
@SpringBootTest
class MyIntegrationTest {
    // ...
}
```

- 테스트 실행 전/후 SQL 스크립트 실행 (TRUNCATE로 테이블 초기화)
- **단점**: 테이블 추가 시마다 SQL 스크립트 수정 필요 → 유지보수 비용 증가

#### 3. @Transactional (롤백)

```java
@Transactional
@SpringBootTest
class MyIntegrationTest {
    // ...
}
```

- 테스트 후 트랜잭션을 롤백하여 DB 상태를 원래대로 유지

**@Transactional 사용 시 주의사항:**

1. **거짓 음성(False Negative) 발생 가능**: OSIV 꺼둔 상태에서 지연 로딩 시 `LazyInitializationException`이 발생해야 하지만, 테스트에서는 트랜잭션이 열려있어 예외가 발생하지 않음 → 프로덕션 코드는 실패하지만 테스트는 통과

   > - **거짓 양성(False Positive)**: 프로덕션 코드는 정상 동작하지만 테스트는 실패
   > - **거짓 음성(False Negative)**: 프로덕션 코드는 실패하지만 테스트는 통과

2. **WebEnvironment RANDOM_PORT/DEFINED_PORT**: 별도 스레드에서 서블릿 컨테이너 실행 → 테스트 트랜잭션 롤백 적용 안 됨

3. **REQUIRES_NEW 전파 레벨**: 새로운 트랜잭션 생성으로 테스트 트랜잭션과 무관하여 롤백 안 됨

4. **비동기 메서드**: 새로운 스레드에서 실행되어 롤백 안 됨

### 참고 자료

- [Martin Fowler - Eradicating Non-Determinism in Tests](https://martinfowler.com/articles/nonDeterminism.html)
- [우아한테크코스 Tecoble - 인수테스트에서 테스트 격리하기](https://tecoble.techcourse.co.kr/post/2020-09-15-test-isolation/)
- [기억보다 기록을 - 테스트 데이터 초기화에 @Transactional 사용하는 것에 대한 생각](https://jojoldu.tistory.com/761)
- [반려생활 - @Transactional 없애려다 오픈소스 라이브러리까지 만든 이야기](https://blog.ban-life.com/transactional-%EC%97%86%EC%95%A0%EB%A0%A4%EB%8B%A4-%EC%98%A4%ED%94%88%EC%86%8C%EC%8A%A4-%EB%9D%BC%EC%9D%B4%EB%B8%8C%EB%9F%AC%EB%A6%AC%EA%B9%8C%EC%A7%80-%EB%A7%8C%EB%93%A0-%EC%9D%B4%EC%95%BC%EA%B8%B0-5426116036bb)

---

## Micrometer (메트릭 수집 라이브러리)

> 출처: https://www.maeil-mail.kr/question/211

### 개념

**Micrometer** 는 **벤더 중립적인 메트릭 계측 라이브러리**로, 애플리케이션에서 발생하는 다양한 지표를 수집합니다.

수집 지표 예시:
- CPU 사용량, 메모리 소비
- HTTP 요청 수 및 응답 시간
- DB 커넥션 풀 상태
- 커스텀 비즈니스 이벤트

### 특징: 벤더 중립성

다양한 모니터링 시스템에 메트릭을 전송할 수 있도록 **단순하고 일관된 API(파사드)** 를 제공합니다. 코드를 변경하지 않고 의존성 추가만으로 모니터링 시스템을 교체할 수 있습니다.

지원 모니터링 시스템: Prometheus, Datadog, Graphite, CloudWatch, InfluxDB 등

### Spring Boot Actuator와의 관계

```
Spring Boot Actuator (관리 엔드포인트 제공)
    └── Micrometer (실제 메트릭 계측 및 모니터링 시스템으로 전송)
            ├── Prometheus
            ├── Datadog
            └── ...
```

- **Spring Boot Actuator**: 애플리케이션 상태, 헬스 체크, 환경, 로그 등 운영 정보를 노출하는 엔드포인트 제공
- **Micrometer**: Actuator 내부에서 JVM, HTTP, DB 등 다양한 메트릭을 실제로 계측하고 모니터링 시스템으로 전송

### 메트릭 타입

#### Counter (카운터)

단조 증가하는 값. 이벤트 발생 횟수를 셀 때 사용합니다.

```java
Counter requestCounter = meterRegistry.counter("custom.requests.total", "endpoint", "/api/test");
requestCounter.increment();
```

#### Timer (타이머)

처리 시간을 측정합니다. count, total time, max time을 함께 기록합니다.

```java
Timer requestTimer = meterRegistry.timer("custom.request.duration", "endpoint", "/api/test");
requestTimer.record(() -> {
    // 측정할 로직
});
```

#### Gauge (게이지)

현재 상태를 나타내는 값. 증가/감소가 모두 가능합니다.

```java
Gauge.builder("custom.active.sessions", activeSessionCount, AtomicInteger::get)
     .tag("region", "us-east")
     .register(meterRegistry);
```

### Prometheus + Grafana 연동

```yaml
# build.gradle
implementation 'io.micrometer:micrometer-registry-prometheus'

# application.yml
management:
  endpoints:
    web:
      exposure:
        include: prometheus, health
```

Prometheus가 `/actuator/prometheus` 엔드포인트를 주기적으로 스크래핑하여 메트릭 수집 → Grafana 대시보드에 시각화
