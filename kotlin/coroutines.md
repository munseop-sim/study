# Kotlin 코루틴 완전 정리

> 최종 수정: 2026-04-21

---

## 1. Coroutine vs Thread

| 항목 | Thread | Coroutine |
|------|--------|-----------|
| 생성 비용 | ~1MB 스택, OS 레벨 | 수십 바이트 힙 객체 |
| 컨텍스트 스위칭 | OS 커널 개입 | JVM 런타임 스케줄링 |
| 블로킹 | 스레드 점유 | suspend (스레드 반환) |
| 개수 | 수백~수천 한계 | 수십만 개 동시 실행 가능 |
| 취소 | interrupt (비협력적) | CancellationException (협력적) |

```kotlin
// Thread: 1만 개 생성 시 OOM 위험
repeat(10_000) { Thread { Thread.sleep(1000) }.start() }

// Coroutine: 1만 개 생성해도 안전
runBlocking {
    repeat(10_000) {
        launch { delay(1000) }
    }
}
```

핵심 차이: 코루틴은 **suspension point**에서 스레드를 반환하고, 재개될 때 같거나 다른 스레드에서 실행된다.

---

## 2. Structured Concurrency (구조적 동시성)

코루틴은 반드시 **CoroutineScope** 안에서 실행되어야 한다.
부모 스코프가 취소되면 모든 자식 코루틴도 취소된다.

```kotlin
// 올바른 구조적 동시성
suspend fun processOrder(orderId: Long) = coroutineScope {
    val user = async { fetchUser(orderId) }
    val inventory = async { checkInventory(orderId) }
    // 두 작업 모두 완료될 때까지 대기
    processPayment(user.await(), inventory.await())
} // 이 블록이 끝나면 모든 자식 코루틴도 종료 보장

// 잘못된 패턴: 스코프를 벗어나는 GlobalScope
fun badPattern() {
    GlobalScope.launch { /* 취소 불가, 메모리 누수 위험 */ }
}
```

**부모-자식 관계 규칙**
1. 자식이 실패하면 부모도 취소된다 (기본 Job)
2. 부모가 취소되면 자식도 취소된다
3. 부모는 모든 자식이 완료될 때까지 완료되지 않는다

---

## 3. suspend 함수

suspend 함수는 **중단 가능한 함수**다. 컴파일러가 CPS(Continuation Passing Style)로 변환한다.

```kotlin
// 컴파일 전
suspend fun fetchUser(id: Long): User {
    delay(100)
    return userRepository.findById(id)
}

// 컴파일 후 (개념적 표현)
fun fetchUser(id: Long, continuation: Continuation<User>): Any {
    // 상태 머신으로 변환됨
}
```

- suspend 함수는 **다른 suspend 함수** 또는 **코루틴 빌더** 안에서만 호출 가능
- `delay()`는 스레드를 블로킹하지 않는 suspend 함수 (cf. `Thread.sleep()`)
- 내부적으로 Continuation 객체가 재개 포인트를 저장

---

## 4. Dispatcher (디스패처)

디스패처는 코루틴이 실행될 스레드 풀을 결정한다.

| Dispatcher | 스레드 풀 | 용도 |
|------------|----------|------|
| `Dispatchers.Default` | CPU 코어 수 (최소 2) | CPU 집약적 연산, 정렬, JSON 파싱 |
| `Dispatchers.IO` | 최대 64개 (또는 코어 수 중 큰 값) | 파일 I/O, DB 쿼리, HTTP 호출 |
| `Dispatchers.Main` | 메인 스레드 단일 | Android UI 업데이트 |
| `Dispatchers.Unconfined` | 호출 스레드 (재개 시 변경 가능) | 테스트, 특수 목적 |

```kotlin
// 실무 패턴
class UserService(
    private val db: UserRepository,
    private val http: HttpClient
) {
    suspend fun getUserWithProfile(id: Long): UserProfile = coroutineScope {
        // IO: DB + HTTP 병렬 처리
        val user = async(Dispatchers.IO) { db.findById(id) }
        val profile = async(Dispatchers.IO) { http.get("/profile/$id") }

        // Default: 결과 가공은 CPU 작업
        withContext(Dispatchers.Default) {
            mergeUserProfile(user.await(), profile.await())
        }
    }
}
```

**withContext vs launch**
- `withContext(Dispatcher)`: 디스패처 전환, 결과 반환 (suspend)
- `launch(Dispatcher)`: 새 코루틴 시작, Job 반환 (fire-and-forget)

---

## 5. CompletableDeferred vs Deferred vs async/await

```kotlin
// Deferred: async {}의 반환 타입, 미래의 값을 나타냄
val deferred: Deferred<User> = async { fetchUser(1L) }
val user: User = deferred.await() // 완료까지 suspend

// CompletableDeferred: 수동으로 완료/실패 설정 가능
val completable = CompletableDeferred<String>()

launch {
    // 외부 콜백을 코루틴으로 래핑할 때 유용
    someCallback { result ->
        completable.complete(result)
    }
}

val result = completable.await()

// async/await 전형적 패턴
suspend fun parallelFetch(): Pair<User, Order> = coroutineScope {
    val userDeferred = async { fetchUser(1L) }
    val orderDeferred = async { fetchOrder(1L) }
    Pair(userDeferred.await(), orderDeferred.await())
    // 순서대로 await해도 병렬 실행됨 (이미 async로 시작했으므로)
}
```

---

## 6. launch vs async

| | `launch` | `async` |
|--|----------|---------|
| 반환 | `Job` | `Deferred<T>` |
| 목적 | 결과 불필요한 작업 | 결과가 필요한 작업 |
| 예외 처리 | 부모로 전파 즉시 | `await()` 호출 시 전파 |

```kotlin
// launch: 로그 저장, 이벤트 발행 등
launch {
    auditLogger.log(event)
}

// async: 값이 필요할 때
val balance: BigDecimal = async { walletService.getBalance(userId) }.await()

// 주의: async 예외는 await() 전까지 숨어있음
val deferred = async { throw RuntimeException("에러") }
// 여기서는 예외가 터지지 않음
deferred.await() // 여기서 예외 발생
```

---

## 7. Job vs SupervisorJob 예외 전파

```kotlin
// 기본 Job: 자식 하나가 실패하면 형제 코루틴도 모두 취소
val scope = CoroutineScope(Job())
scope.launch {
    launch { throw RuntimeException("자식1 실패") } // 전체 스코프 취소
    launch { delay(1000); println("자식2") }        // 실행 안 됨
}

// SupervisorJob: 자식 실패가 형제에게 전파 안 됨
val supervisorScope = CoroutineScope(SupervisorJob())
supervisorScope.launch {
    launch { throw RuntimeException("자식1 실패") } // 이 코루틴만 실패
    launch { delay(1000); println("자식2") }        // 정상 실행됨
}
```

**실무 판단 기준**
- 모든 작업이 성공해야 의미있는 경우 → `Job` (예: 송금 처리 단계)
- 일부 실패해도 나머지는 계속돼야 하는 경우 → `SupervisorJob` (예: 알림 발송)

---

## 8. coroutineScope vs supervisorScope

```kotlin
// coroutineScope: 자식 하나 실패 → 전체 취소
suspend fun processPayment(orderId: Long) = coroutineScope {
    val validation = async { validateOrder(orderId) }
    val deduction = async { deductBalance(orderId) }
    // validation 실패 시 deduction도 취소됨 → 데이터 일관성 보장
    validation.await()
    deduction.await()
}

// supervisorScope: 자식 실패가 다른 자식에게 전파 안 됨
suspend fun sendNotifications(userId: Long) = supervisorScope {
    launch { sendEmailNotification(userId) }    // 실패해도
    launch { sendPushNotification(userId) }     // 계속 실행
    launch { sendSmsNotification(userId) }      // 계속 실행
}
```

---

## 9. Java 상호운용 (runBlocking 주의점)

```kotlin
// runBlocking: 코루틴을 블로킹 방식으로 실행 (테스트/메인 진입점용)
fun main() = runBlocking {
    val result = fetchUser(1L)
    println(result)
}

// 주의: 서버 코드에서 runBlocking 사용 금지
class UserController {
    // 절대 하면 안 됨: 스레드 풀 고갈 위험
    fun getUser(id: Long): User = runBlocking {
        fetchUser(id) // IO 스레드에서 runBlocking → 데드락 가능
    }

    // 올바른 방법: Spring WebFlux or suspend controller
    suspend fun getUser(id: Long): User = fetchUser(id)
}

// Java → Kotlin 코루틴 호출
// Java에서는 Future로 변환
val future: CompletableFuture<User> = GlobalScope.future {
    fetchUser(1L)
}

// 콜백 → 코루틴 변환
suspend fun <T> awaitCallback(block: (Callback<T>) -> Unit): T =
    suspendCoroutine { continuation ->
        block { result ->
            continuation.resume(result)
        }
    }
```

---

## 10. 실무 패턴

### 병렬 API 호출

```kotlin
// 순차 실행 (느림): 총 300ms
suspend fun slowVersion(orderId: Long): OrderDetail {
    val user = fetchUser(orderId)       // 100ms
    val inventory = fetchInventory(orderId) // 100ms
    val price = fetchPrice(orderId)     // 100ms
    return OrderDetail(user, inventory, price)
}

// 병렬 실행 (빠름): 총 100ms
suspend fun fastVersion(orderId: Long): OrderDetail = coroutineScope {
    val user = async { fetchUser(orderId) }
    val inventory = async { fetchInventory(orderId) }
    val price = async { fetchPrice(orderId) }
    OrderDetail(user.await(), inventory.await(), price.await())
}
```

### withTimeout

```kotlin
suspend fun fetchWithTimeout(id: Long): User? {
    return try {
        withTimeout(3000L) { // 3초 초과 시 TimeoutCancellationException
            fetchUser(id)
        }
    } catch (e: TimeoutCancellationException) {
        logger.warn("사용자 조회 타임아웃: id=$id")
        null // fallback
    }
}

// withTimeoutOrNull: 타임아웃 시 null 반환 (예외 없음)
val user = withTimeoutOrNull(3000L) { fetchUser(id) }
```

### Flow를 이용한 스트리밍

```kotlin
fun getTransactionStream(userId: Long): Flow<Transaction> = flow {
    while (true) {
        val transactions = fetchNewTransactions(userId)
        transactions.forEach { emit(it) }
        delay(5000)
    }
}

// 수집
launch {
    getTransactionStream(userId)
        .filter { it.amount > BigDecimal("1000000") }
        .collect { transaction ->
            sendHighAmountAlert(transaction)
        }
}
```

---

## 11. 면접 포인트

**Q1. suspend 함수는 어떻게 동작하나요?**
> 컴파일러가 CPS 변환을 통해 상태 머신으로 변환합니다. 각 suspension point에서 현재 상태(로컬 변수, 재개 위치)를 Continuation 객체에 저장하고 스레드를 반환합니다. 재개 시 저장된 상태를 복원하여 실행을 계속합니다.

**Q2. Job과 SupervisorJob의 차이는?**
> Job은 자식 코루틴의 실패가 부모와 다른 형제 코루틴에게 전파되어 전체가 취소됩니다. SupervisorJob은 자식의 실패가 형제에게 전파되지 않아 독립적으로 실행됩니다. 서비스 계층에서 독립적인 알림 발송, 로깅 등에 SupervisorJob을 씁니다.

**Q3. runBlocking을 서버 코드에서 쓰면 안 되는 이유는?**
> runBlocking은 현재 스레드를 블로킹합니다. Netty나 Tomcat의 요청 처리 스레드에서 runBlocking을 호출하면 해당 스레드가 점유되어 다른 요청을 처리하지 못합니다. 특히 내부에서 Dispatchers.IO를 쓰는 경우 데드락이 발생할 수 있습니다.

**Q4. coroutineScope vs withContext 차이는?**
> coroutineScope는 새로운 스코프를 만들어 내부에서 여러 코루틴을 병렬 실행할 수 있게 합니다. withContext는 단일 코루틴의 디스패처를 변경하는 용도입니다. 병렬 작업에는 coroutineScope + async, 스레드 전환에는 withContext를 씁니다.

**Q5. Deferred와 CompletableFuture의 차이는?**
> Deferred는 코루틴 세계의 비동기 값으로, await() 시 suspend(스레드 비블로킹)됩니다. CompletableFuture는 Java의 비동기 값으로, get() 시 스레드를 블로킹합니다. 코루틴과 레거시 Java 코드 연동 시 `future {}` 빌더나 `await()` 확장 함수로 변환합니다.
