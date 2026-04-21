# Hexagonal Architecture (Ports & Adapters) — 포트와 어댑터 아키텍처

## 1. Layered Architecture 복습

전통적인 계층형 아키텍처는 가장 보편적인 구조다.

```
┌─────────────────────┐
│   Presentation      │  ← Controller, DTO
├─────────────────────┤
│   Application       │  ← Service, UseCase
├─────────────────────┤
│   Domain            │  ← Entity, Domain Logic
├─────────────────────┤
│   Infrastructure    │  ← Repository, DB, External API
└─────────────────────┘
```

### 계층별 책임

| 계층 | 책임 | 예시 |
|---|---|---|
| Presentation | HTTP 요청/응답 변환 | `@Controller`, `@RestController` |
| Application | 유스케이스 조율 | `@Service`, 트랜잭션 경계 |
| Domain | 비즈니스 규칙 | `Entity`, `ValueObject`, `DomainService` |
| Infrastructure | 외부 시스템 연동 | `JpaRepository`, `KafkaProducer`, `RestClient` |

### 순환 의존 문제

계층형 아키텍처의 가장 큰 문제는 **도메인 계층이 인프라 계층에 의존**하게 된다는 점이다.

```
// 나쁜 예: Domain 계층이 JPA에 직접 의존
class TradeService {
    private final TradeJpaRepository tradeJpaRepository  // JPA 구현체에 직접 의존
}
```

이렇게 되면:
- `TradeJpaRepository`를 다른 구현체(예: MongoDB)로 교체하기 어려움
- 도메인 로직 단위 테스트 시 DB가 반드시 필요
- 인프라 변경이 도메인 코드 수정을 강제함

---

## 2. Hexagonal Architecture (Ports & Adapters)

Alistair Cockburn이 2005년에 제안한 아키텍처. 핵심 원칙은 **도메인(애플리케이션 코어)을 외부 세계로부터 격리**하는 것이다.

```
        [Primary Adapter]                    [Secondary Adapter]
        (Driving Side)                       (Driven Side)

  HTTP Controller  ─────┐           ┌─────  JPA Repository
  CLI Command      ─────┤  Domain   ├─────  Kafka Producer
  Event Consumer   ─────┘  (Core)   └─────  Redis Cache
                         │         │
                    [Inbound Port]  [Outbound Port]
                    (Use Case I/F)  (Repository I/F)
```

### 2.1 핵심 개념

**도메인(Application Core)**
- 비즈니스 로직만 존재
- 외부 프레임워크(Spring, JPA 등)에 의존하지 않음
- 어떠한 외부 시스템도 알지 못함

**Port**
- 도메인이 외부 세계와 대화하는 인터페이스
- Java/Kotlin의 `interface`로 표현

**Adapter**
- Port를 구현하거나 호출하는 외부 컴포넌트
- 프레임워크, DB 드라이버, HTTP 클라이언트 등이 여기에 해당

### 2.2 Inbound Port (Use Case Interface)

외부에서 도메인으로 들어오는 요청을 정의하는 인터페이스.

```kotlin
// Inbound Port: 도메인이 외부에 제공하는 기능 정의
interface CreateTradeUseCase {
    fun createTrade(command: CreateTradeCommand): TradeId
}

interface GetTradeQuery {
    fun getTrade(tradeId: TradeId): TradeDetail
}
```

도메인 서비스가 이 인터페이스를 구현한다.

```kotlin
// 도메인 서비스 = Inbound Port 구현체
class TradeCommandService(
    private val tradeRepository: TradeRepository,  // Outbound Port
    private val fxRateProvider: FxRateProvider     // Outbound Port
) : CreateTradeUseCase {

    override fun createTrade(command: CreateTradeCommand): TradeId {
        val fxRate = fxRateProvider.getCurrentRate(command.fromCurrency, command.toCurrency)
        val trade = Trade.create(command, fxRate)
        return tradeRepository.save(trade).id
    }
}
```

### 2.3 Outbound Port (Repository/Provider Interface)

도메인이 외부 시스템에 요청하는 인터페이스. 도메인 패키지 안에 위치한다.

```kotlin
// Outbound Port: 도메인이 외부에 요청하는 기능 정의
// 이 인터페이스는 도메인 패키지 안에 있음
interface TradeRepository {
    fun save(trade: Trade): Trade
    fun findById(id: TradeId): Trade?
    fun findByUserId(userId: UserId): List<Trade>
}

interface FxRateProvider {
    fun getCurrentRate(from: Currency, to: Currency): FxRate
}
```

### 2.4 Primary Adapter (Driving Adapter)

외부 세계 → 도메인으로 요청을 전달하는 어댑터. 도메인의 Inbound Port를 호출한다.

```kotlin
// Primary Adapter: HTTP Controller
@RestController
@RequestMapping("/api/v1/trades")
class TradeController(
    private val createTradeUseCase: CreateTradeUseCase,  // Inbound Port 사용
    private val getTradeQuery: GetTradeQuery
) {
    @PostMapping
    fun createTrade(@RequestBody request: CreateTradeRequest): ApiResponse<TradeIdResponse> {
        val command = request.toCommand()
        val tradeId = createTradeUseCase.createTrade(command)
        return ApiResponse.success(TradeIdResponse(tradeId.value))
    }
}
```

### 2.5 Secondary Adapter (Driven Adapter)

도메인 → 외부 시스템으로 요청을 전달하는 어댑터. 도메인의 Outbound Port를 구현한다.

```kotlin
// Secondary Adapter: JPA 구현체
@Repository
class TradeJpaAdapter(
    private val tradeJpaRepository: TradeJpaRepository
) : TradeRepository {  // Outbound Port 구현

    override fun save(trade: Trade): Trade {
        val entity = TradeEntity.fromDomain(trade)
        val saved = tradeJpaRepository.save(entity)
        return saved.toDomain()
    }

    override fun findById(id: TradeId): Trade? {
        return tradeJpaRepository.findById(id.value)
            ?.toDomain()
    }
}
```

### 2.6 의존성 방향

```
HTTP Controller (Primary Adapter)
        ↓ 호출
  CreateTradeUseCase (Inbound Port)  ←── 구현: TradeCommandService (Domain)
        ↓ 도메인이 호출
  TradeRepository (Outbound Port)   ←── 구현: TradeJpaAdapter (Secondary Adapter)
        ↓ 호출
   TradeJpaRepository (JPA)
```

**의존성 규칙**: 외부 → Port → 도메인 (항상 안쪽 방향)
도메인은 어떤 외부 구현체도 알지 못한다. 이것이 핵심이다.

---

## 3. Clean Architecture와의 비교

Robert C. Martin(Uncle Bob)의 Clean Architecture와 Hexagonal Architecture는 핵심 철학이 같다.

### Clean Architecture 계층

```
┌─────────────────────────────────────┐
│  Frameworks & Drivers (최외곽)       │  Spring, JPA, Web
├─────────────────────────────────────┤
│  Interface Adapters                 │  Controller, Gateway, Presenter
├─────────────────────────────────────┤
│  Use Cases (Application Business)   │  Application Service
├─────────────────────────────────────┤
│  Entities (Enterprise Business)     │  Domain Entity, Value Object
└─────────────────────────────────────┘
```

### 공통점: 의존성 규칙

- **안쪽 레이어는 바깥쪽을 절대 알지 못한다**
- 의존성 방향은 항상 안쪽(비즈니스 규칙)을 향한다
- 외부 프레임워크로부터 비즈니스 로직을 보호한다

### 차이점

| 항목 | Hexagonal | Clean Architecture |
|---|---|---|
| 레이어 수 | 3 (Core, Port, Adapter) | 4 (Entity, UseCase, Adapter, Framework) |
| 명명 | Port/Adapter | Use Case/Gateway/Controller |
| 엄격함 | 상대적으로 유연 | Entity와 UseCase를 명시적으로 분리 |
| 적용 범위 | 외부 의존성 격리에 집중 | 전체 아키텍처 설계 원칙 |
| Entity 위치 | Domain Core 안에 혼재 가능 | 가장 안쪽 레이어로 명시적 분리 |

실무에서는 두 아키텍처를 혼용하는 경우가 많다. "Hexagonal하게 패키지를 나누되, 내부에서는 Clean Architecture의 Entity/UseCase 분리를 적용"하는 방식이다.

---

## 4. 언제 Hexagonal을 선택하는가

### Hexagonal이 유리한 경우

1. **외부 의존성 교체 가능성이 높을 때**
   - DB를 PostgreSQL → DynamoDB로 바꿀 가능성
   - 외부 API 벤더가 바뀔 가능성
   - Outbound Port만 구현한 새 Adapter로 교체 가능

2. **도메인 로직이 복잡하고 중요할 때**
   - 복잡한 비즈니스 규칙이 외부 프레임워크와 섞이면 테스트하기 어려움
   - 도메인을 격리하면 단위 테스트가 쉬워짐

3. **팀 규모가 크고 도메인 단위로 개발 분리가 필요할 때**
   - 도메인 팀과 인프라 팀이 Port 계약(인터페이스)만 합의하면 독립 개발 가능

4. **장기적으로 유지보수가 중요한 프로젝트**

### Layered Architecture가 더 적합한 경우

1. **빠른 프로토타입, PoC**: 초기 속도가 중요할 때
2. **단순 CRUD 서비스**: 도메인 로직이 거의 없는 경우
3. **소규모 팀, 단기 프로젝트**: 아키텍처 오버헤드가 이득보다 클 때
4. **외부 의존성 변경 가능성이 없을 때**

---

## 5. Aggregate 경계 설계 (DDD 연계)

Hexagonal Architecture는 DDD(Domain-Driven Design)와 자연스럽게 결합된다.

### Aggregate Root

```kotlin
// Aggregate Root: Trade
class Trade private constructor(
    val id: TradeId,
    val userId: UserId,
    private var status: TradeStatus,
    private val items: MutableList<TradeItem>
) {
    // Aggregate 내부 일관성 보장
    fun complete(completedAt: OffsetDateTime) {
        check(status == TradeStatus.PROCESSING) {
            "완료 처리는 PROCESSING 상태에서만 가능합니다."
        }
        status = TradeStatus.COMPLETED
        // 도메인 이벤트 기록
        registerEvent(TradeCompletedEvent(id, userId, completedAt))
    }

    // 외부에서 직접 items를 수정하지 못하도록 불변 뷰 제공
    fun getItems(): List<TradeItem> = items.toList()
}
```

**Aggregate 경계 원칙**
- Aggregate 내부는 단일 트랜잭션으로 일관성 보장
- Aggregate 간 참조는 ID로만 (직접 객체 참조 금지)
- 하나의 트랜잭션에서는 하나의 Aggregate만 변경

### 도메인 이벤트로 Aggregate 간 통신

```kotlin
// Aggregate Root에서 이벤트 발행
class Trade : AbstractAggregateRoot<Trade>() {
    fun complete(completedAt: OffsetDateTime) {
        status = TradeStatus.COMPLETED
        registerEvent(TradeCompletedEvent(id, userId, completedAt))
    }
}

// Application Service에서 이벤트 저장 (Outbox와 연계)
@Transactional
fun completeTrade(command: CompleteTradeCommand) {
    val trade = tradeRepository.findById(command.tradeId)
        ?: throw TradeNotFoundException(command.tradeId)

    trade.complete(command.completedAt)
    tradeRepository.save(trade)
    // AbstractAggregateRoot의 도메인 이벤트가
    // Spring의 ApplicationEventPublisher를 통해 발행됨
}
```

---

## 6. Spring Boot에서 Hexagonal 구현 패키지 구조

```
src/main/kotlin/com/sentbiz/trade/
├── application/                      # Application Core
│   ├── domain/                       # 도메인 모델
│   │   ├── Trade.kt                  # Aggregate Root
│   │   ├── TradeItem.kt
│   │   ├── TradeStatus.kt
│   │   └── TradeId.kt
│   ├── port/
│   │   ├── inbound/                  # Inbound Ports (Use Case Interfaces)
│   │   │   ├── CreateTradeUseCase.kt
│   │   │   ├── CompleteTradeUseCase.kt
│   │   │   └── GetTradeQuery.kt
│   │   └── outbound/                 # Outbound Ports
│   │       ├── TradeRepository.kt
│   │       ├── FxRateProvider.kt
│   │       └── TradeEventPublisher.kt
│   └── service/                      # 도메인 서비스 (Inbound Port 구현)
│       ├── TradeCommandService.kt
│       └── TradeQueryService.kt
│
└── adapter/                          # Adapters (외부 세계)
    ├── inbound/                      # Primary Adapters
    │   ├── web/
    │   │   ├── TradeController.kt
    │   │   ├── TradeRequest.kt
    │   │   └── TradeResponse.kt
    │   └── event/
    │       └── TradeEventConsumer.kt # Kafka Consumer
    └── outbound/                     # Secondary Adapters
        ├── persistence/
        │   ├── TradeJpaAdapter.kt    # TradeRepository 구현
        │   ├── TradeEntity.kt
        │   └── TradeJpaRepository.kt
        ├── external/
        │   └── FxRateRestAdapter.kt  # FxRateProvider 구현
        └── messaging/
            └── TradeKafkaAdapter.kt  # TradeEventPublisher 구현
```

---

## 7. 테스트 용이성

Hexagonal의 큰 장점 중 하나는 테스트가 쉽다는 것이다.

```kotlin
// 도메인 서비스 단위 테스트 — DB, Kafka 없이 가능
class TradeCommandServiceTest : FunSpec({
    val tradeRepository = mockk<TradeRepository>()       // Outbound Port Mock
    val fxRateProvider = mockk<FxRateProvider>()         // Outbound Port Mock
    val service = TradeCommandService(tradeRepository, fxRateProvider)

    test("거래 생성 시 환율이 적용된 수신 금액이 계산된다") {
        val command = CreateTradeCommand(userId = UserId(1L), ...)
        val mockRate = FxRate(1350.0)

        every { fxRateProvider.getCurrentRate(any(), any()) } returns mockRate
        every { tradeRepository.save(any()) } answers { firstArg() }

        val tradeId = service.createTrade(command)
        tradeId shouldNotBe null
    }
})
```

---

## 면접 포인트

### Q1. "Hexagonal Architecture를 왜 사용하나요?"
- 도메인(비즈니스 로직)을 외부 프레임워크, DB, 메시징 시스템으로부터 격리하기 위해서다.
- 외부 의존성이 바뀌어도 도메인 코드는 변경되지 않는다.
- 도메인을 단독으로 단위 테스트할 수 있어 테스트 속도와 커버리지가 향상된다.

### Q2. "Inbound Port와 Outbound Port의 차이는?"
- Inbound Port: 외부에서 도메인으로 들어오는 요청의 인터페이스. 도메인이 외부에 제공하는 기능. Controller가 이를 호출한다.
- Outbound Port: 도메인이 외부에 요청하는 기능의 인터페이스. 도메인 패키지 안에 정의된다. JPA Adapter가 이를 구현한다.
- 핵심: Outbound Port는 도메인 안에 있고, 인프라 어댑터가 이를 구현함으로써 의존성 역전이 발생한다.

### Q3. "Layered Architecture 대신 Hexagonal을 선택하는 기준은?"
- 도메인 로직이 복잡하고 외부 의존성 교체 가능성이 있을 때 Hexagonal을 선택한다.
- 빠른 개발이 필요한 단순 CRUD 서비스라면 오히려 Layered가 적합하다.
- 과도한 추상화는 오히려 생산성을 저해할 수 있으므로 상황에 맞게 선택해야 한다.

### Q4. "Hexagonal Architecture와 Clean Architecture의 차이는?"
- 핵심 철학(의존성 방향은 항상 안쪽)은 동일하다.
- Clean Architecture는 Entity와 Use Case를 명시적으로 레이어로 분리하여 더 엄격하다.
- Hexagonal은 Port와 Adapter 개념에 집중하며 상대적으로 유연하다.
- 실무에서는 두 아키텍처를 혼합하여 사용하는 경우가 많다.

### Q5. "DDD의 Aggregate와 Hexagonal은 어떻게 연계되나요?"
- Aggregate는 도메인 코어 안에 위치한다. Hexagonal의 격리 덕분에 Aggregate가 외부에 오염되지 않는다.
- Aggregate 간 통신은 도메인 이벤트로 하고, 이 이벤트가 Outbound Port(EventPublisher)를 통해 Kafka 등으로 발행된다.
- Outbox 패턴과 연계하면 도메인 이벤트의 안전한 발행을 보장할 수 있다.

### Q6. "Secondary Adapter를 Mock으로 교체하면 어떤 이점이 있나요?"
- 도메인 서비스 테스트 시 DB, Kafka 등 실제 인프라 없이 빠른 단위 테스트가 가능하다.
- 인프라 Adapter는 통합 테스트(Testcontainers 등)로 별도 검증하면 테스트 계층이 명확해진다.
- 외부 API(환율 제공사, 결제사 등) 의존을 Mock으로 대체하여 테스트 안정성을 높인다.
