# Java Concurrency

## 스레드 생성 방식

- `Thread` 클래스 상속
- `Runnable` 인터페이스 구현
- `Callable` + `ExecutorService` 사용 (결과값 반환 가능)

## 비동기 처리 도구

- `Future`: 결과 대기 시 블로킹 가능
- `CompletableFuture`: 콜백 체이닝 기반 비동기 처리
- `ExecutorService`: 스레드 풀/작업 실행 관리

## 동시성 제어

- `Atomic` 타입: CAS(Compare-And-Swap) 연산 기반의 락-프리(lock-free) 동시성 제어
- `synchronized`: 메서드/블록에 모니터 락을 적용하여 한 번에 하나의 스레드만 접근
- `volatile`: 메인 메모리에서 직접 읽고 쓰도록 보장하여 가시성(Visibility) 문제 해결
- `Lock` 인터페이스 (`ReentrantLock`): `synchronized`보다 세밀한 락 제어 가능

## ThreadLocal

**ThreadLocal**은 각 스레드가 독립적인 변수 복사본을 가질 수 있도록 해주는 클래스입니다. 스레드마다 별도의 값을 저장하므로, 동기화 없이 스레드 안전한 데이터 저장이 가능합니다.

```java
private static final ThreadLocal<String> threadLocal = new ThreadLocal<>();

// 값 저장
threadLocal.set("스레드별 값");

// 값 조회
String value = threadLocal.get();

// 값 제거 (중요!)
threadLocal.remove();
```

### 사용 사례

- **Spring Security**: SecurityContextHolder에서 현재 인증 정보를 스레드별로 저장
- **Spring Transaction**: 트랜잭션 컨텍스트를 스레드별로 유지
- **MDC (Mapped Diagnostic Context)**: 로깅 시 요청별 고유 ID(예: requestId)를 스레드별로 저장
- **데이터베이스 커넥션**: 스레드별 커넥션 바인딩

### 주의사항: 메모리 릭 (Memory Leak)

스레드 풀(Thread Pool) 환경에서 ThreadLocal을 사용할 때 **메모리 릭**이 발생할 수 있습니다.

스레드 풀의 스레드는 재사용되기 때문에, ThreadLocal 값을 `remove()` 하지 않으면 이전 요청의 데이터가 다음 요청에서 그대로 남아있을 수 있습니다.

```java
// 잘못된 사용 예시 (remove 누락)
public void handleRequest() {
    threadLocal.set(getCurrentUser());
    try {
        processRequest();
    } finally {
        threadLocal.remove(); // 반드시 remove() 호출!
    }
}
```

**문제점:**
1. 이전 요청의 데이터가 다음 요청에 노출될 수 있음 (보안 이슈)
2. 참조가 끊어지지 않아 GC 대상이 되지 않아 메모리 릭 발생

**해결 방법**: 사용이 끝난 후 반드시 `threadLocal.remove()`를 호출하거나, `try-finally` 블록으로 보장합니다.

## 멀티쓰레딩 (Multithreading)

### 멀티쓰레딩의 장점

- **성능 향상**: CPU 코어를 효율적으로 활용하여 처리량(Throughput) 증가
- **응답성 개선**: I/O 작업 대기 중에도 다른 작업을 계속 수행 가능
- **자원 공유**: 같은 프로세스 내의 스레드는 메모리를 공유하여 IPC(Inter-Process Communication)보다 효율적

### 멀티쓰레딩의 단점

- **동시성 문제**: 여러 스레드가 공유 자원에 동시에 접근할 때 예측 불가능한 결과 발생 가능
- **교착 상태(Deadlock)**: 두 개 이상의 스레드가 서로의 락을 기다리며 무한 대기 상태
- **기아 상태(Starvation)**: 특정 스레드가 CPU 자원을 영원히 할당받지 못하는 상태
- **복잡성 증가**: 디버깅과 테스트가 어려워짐

### Thread-safe 보장 방법

**1. synchronized 키워드**
```java
public synchronized void increment() {
    count++;
}
```
메서드 또는 블록에 락을 적용하여 한 번에 하나의 스레드만 접근 가능.

**2. volatile 키워드**
```java
private volatile boolean running = true;
```
변수를 CPU 캐시가 아닌 메인 메모리에서 직접 읽고 써서 가시성(Visibility) 보장. 단, 복합 연산(i++)의 원자성은 보장하지 않음.

**3. Atomic 클래스**
```java
private AtomicInteger count = new AtomicInteger(0);
count.incrementAndGet(); // CAS 기반 원자적 연산
```
`java.util.concurrent.atomic` 패키지의 클래스들. 락 없이 원자적 연산을 보장.

**4. ReentrantLock**
```java
private final Lock lock = new ReentrantLock();

public void increment() {
    lock.lock();
    try {
        count++;
    } finally {
        lock.unlock();
    }
}
```
`synchronized`보다 세밀한 락 제어 가능. tryLock(), 타임아웃 설정 등 지원.

**5. 불변 객체 사용**
불변 객체는 상태가 변경되지 않으므로 동시성 문제가 발생하지 않음. `String`, `Integer` 등의 불변 클래스와 `final` 키워드 활용.

**6. Thread-safe 컬렉션 사용**
- `ConcurrentHashMap`: HashMap의 Thread-safe 버전
- `CopyOnWriteArrayList`: 쓰기 시 복사하여 Thread-safe 보장
- `BlockingQueue`: 스레드 간 안전한 데이터 교환을 위한 큐

### 동시성 관련 주요 개념

**가시성(Visibility)**: 한 스레드에서 변경한 값이 다른 스레드에서 즉시 보이는 성질. CPU 캐시로 인해 보장되지 않을 수 있음.

**원자성(Atomicity)**: 연산이 쪼개지지 않고 완전히 실행되거나 전혀 실행되지 않는 성질. `i++`는 읽기-수정-쓰기의 세 단계로 원자적이지 않음.

**순서 보장(Ordering)**: JVM과 CPU가 성능 최적화를 위해 명령 순서를 재정렬할 수 있음. `volatile`, `synchronized`로 순서 보장 가능.
