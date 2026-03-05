# 동시성 제어

## 동기 vs 비동기 / 블로킹 vs 논블로킹

### 동기(Synchronous) vs 비동기(Asynchronous)

동기와 비동기는 **호출하는 함수의 작업 완료를 기다리는지 여부**의 차이입니다.

- **동기**: 함수 A가 동기로 함수 B를 호출하면, A는 B의 작업이 완료될 때까지 기다려야 합니다. 작업이 순차적으로 진행됩니다.
- **비동기**: 함수 A가 비동기로 함수 B를 호출하면, A는 B의 작업 완료를 신경 쓰지 않고 따로 동작합니다. 작업이 순차적으로 진행되지 않습니다.

### 블로킹(Blocking) vs 논블로킹(Non-blocking)

- **블로킹**: 함수가 호출된 후, 호출한 함수의 결과를 기다리기 위해 실행을 멈추는 상태. 제어권이 반환되지 않고 대기하는 상황입니다.
- **동기**: 작업이 순차적으로 진행되는 것을 의미 (블로킹과 유사하나 다름)

### Spring에서의 비동기 처리 (`@Async`)

스프링에서는 `@Async` 어노테이션을 사용하여 비동기 처리를 수행할 수 있습니다. 주의사항:

- `@Async`가 적용된 메서드에서 발생하는 **예외는 호출자에게 전파되지 않습니다.** 별도의 비동기 예외 처리기를 사용해야 합니다.
- **프록시 기반으로 동작**하기 때문에 같은 클래스 내부에서 직접 호출 시 별도의 스레드에서 실행되지 않습니다.
- 비동기 메서드 내에서 생성한 **트랜잭션은 상위 트랜잭션과 무관한 생명주기**를 가집니다.

---

## 동시성(Concurrency) vs 병렬성(Parallelism)

### 동시성(Concurrency)

실제로 여러 작업을 동시에 수행하는 것이 아니라, **논리적으로 동시에 실행되는 것처럼 보이게 만드는** 개념입니다.

- 단일 코어를 기준으로 시간 분할(Time Slicing)을 통해 여러 스레드를 번갈아 가며 작업을 수행
- I/O 작업 시 CPU가 유휴 상태로 대기하는 대신, 컨텍스트 스위칭을 통해 다른 스레드의 작업 처리
- 서버가 여러 클라이언트의 요청을 동시에 처리할 수 있게 함
- 주의: **Deadlock, Race Condition, Starvation** 등의 문제가 발생할 수 있음

### 병렬성(Parallelism)

물리적으로 **동일한 시간에 여러 작업을 독립적으로 수행**하는 것입니다.

- 여러 개의 코어가 각각 독립된 스레드의 작업을 동시에 처리
- 고성능 컴퓨팅(HPC)에 이상적
- 데이터나 리소스 공유 시 동기화가 필요하여 오버헤드가 발생할 수 있음

| 구분 | 동시성 | 병렬성 |
|------|--------|--------|
| 실행 방식 | 논리적 동시 실행 (번갈아 가며) | 물리적 동시 실행 |
| 필요 코어 | 단일 코어도 가능 | 멀티 코어 필요 |
| 목적 | CPU 활용률 향상 | 처리 속도 향상 |

---

## 경쟁 상태(Race Condition)

### 정의

**경쟁 상태(Race Condition)**는 두 개 이상의 스레드가 공유 자원에 동시에 접근할 때 스레드 간의 실행 순서에 따라 결과가 달라지는 현상입니다.

### 해결을 위해 보장되어야 하는 두 가지 성질

#### 원자성(Atomicity)

공유 자원에 대한 작업의 단위가 더 이상 쪼갤 수 없는 **하나의 연산처럼 동작**하는 성질입니다.

- `i++` 연산은 하나의 문장이지만 CPU는 세 단계로 분리: **Read → Modify → Write**
- 연산 사이에 다른 스레드가 개입하면 기대하지 않은 결과 발생

#### 가시성(Visibility)

한 스레드에서 변경한 값이 다른 스레드에서 **즉시 확인 가능**한 성질입니다.

- 현대 CPU는 각 코어마다 CPU 캐시가 존재
- 한 스레드가 공유 자원을 변경해도 변경된 값이 메인 메모리에 언제 반영될지 알 수 없음
- 다른 스레드가 변경 사항을 즉시 확인할 수 없는 문제 발생

### Java에서의 해결 방법

| 방법 | 원자성 | 가시성 |
|------|--------|--------|
| `synchronized` | O | O |
| `Atomic` 클래스 (CAS) | O | O |
| `ReentrantLock` | O | O |
| `volatile` | X | O (단일 쓰기 스레드 전제) |
| Concurrent Collections | O | O |

> `volatile`은 가시성만 보장하며, **하나의 스레드에서만 쓰기 작업**을 수행하고 나머지는 읽기만 수행해야 합니다.

---

## 교착 상태(Deadlock)

### 정의

**교착 상태(Deadlock)**는 두 개 이상의 작업이 서로 상대방의 작업이 끝나기만을 기다리고 있어 결과적으로 아무것도 완료되지 못하는 상태입니다.

### 교착 상태 발생 조건 (4가지 모두 만족 시 발생)

1. **상호 배제(Mutual Exclusion)**: 한 프로세스가 사용하는 자원을 다른 프로세스가 사용할 수 없음
2. **점유 대기(Hold and Wait)**: 자원을 할당받은 상태에서 다른 자원을 할당받기를 기다리는 상태
3. **비선점(Non-preemption)**: 자원이 강제로 해제될 수 없으며, 점유 프로세스 작업이 끝난 이후에만 해제
4. **원형 대기(Circular Wait)**: 프로세스들이 원의 형태로 자원을 대기

### Java에서의 교착 상태 해결

```java
// 교착 상태 발생 예시
// thread 1: synchronized(resource1) { synchronized(resource2) { ... } }
// thread 2: synchronized(resource2) { synchronized(resource1) { ... } }
```

**해결 방법:**
- **점유 대기 제거**: `synchronized` 중첩 블록을 없애 잠금 순서를 통일
- **`ReentrantLock.tryLock()`**: 타임아웃 설정으로 무한 대기 방지
- **`lockInterruptibly()`**: 데드락 발생 시 인터럽트로 스레드를 깨움
- **4가지 조건 중 하나를 충족하지 못하게** 하거나, **대기 시 무한정 기다리지 않는** 방식으로 해결

---

## 스레드 풀 포화 정책(Saturation Policies)

### 정의

**스레드 풀 포화 정책**이란 스레드 풀이 포화 상태일 때의 행동을 결정하는 정책입니다.

`ThreadPoolExecutor` 설정:
- `corePoolSize`: 상시 유지하는 스레드 수
- `workQueueSize`: 작업 대기열 크기
- `maxPoolSize`: 스레드를 추가할 수 있는 최대 수

스레드가 `maxPoolSize`까지 늘어나고 대기열까지 꽉 찬 상태 = **포화 상태**

새로운 작업 요청이 들어오면 `RejectedExecutionHandler`의 구현체인 포화 정책이 실행됩니다.

### 포화 정책 종류

| 정책 | 동작 |
|------|------|
| **AbortPolicy** (기본값) | `RejectedExecutionException` 예외 발생 |
| **DiscardPolicy** | 신규 요청을 무시 |
| **DiscardOldestPolicy** | 대기열에서 가장 오래된 요청을 버리고 신규 요청을 대기열에 추가 |
| **CallerRunsPolicy** | 요청을 제출한 스레드(호출자 스레드)에서 해당 작업을 직접 실행 |

### 커스텀 포화 정책

```java
class CustomPolicy implements RejectedExecutionHandler {
    @Override
    public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
        // 커스텀 처리 로직
    }
}
```

---

## 코루틴 vs 스레드

### 메모리 사용량 차이

| 구분 | 스레드 | 코루틴 |
|------|--------|--------|
| 스택 메모리 | JVM 기본 약 1MB | 자체 스택 불필요, 수 KB만 사용 |
| 메모리 예약 | 생성 시 예약, 종료까지 유지 | heap에 실행 지점과 로컬 변수 상태만 저장 |

### 컨텍스트 스위칭 비용

- **스레드**: 운영체제 수준의 컨텍스트 스위칭 필요. CPU 레지스터, 메모리 맵 등의 상태를 저장/복원하는 비용이 큼
- **코루틴**: 운영체제 개입 없이 **사용자 공간(user space)**에서 전환. 실행 지점과 로컬 변수 상태만 저장하면 됨

### 생성 및 관리 비용

- **스레드**: 운영체제에 시스템 호출 필요, 커널 수준의 리소스 할당
- **코루틴**: 단순한 객체 할당과 유사, 운영체제 리소스를 직접 소비하지 않음

### 일시 중단 메커니즘

- **스레드**: 블로킹 작업(I/O 등) 중에는 완전히 차단되어 다른 작업 수행 불가
- **코루틴**: `suspend` 함수에서 실행을 중단하고 기본 스레드를 해제하여 다른 코루틴이 사용 가능. 수천 개의 코루틴을 소수의 스레드에서 효율적으로 실행 가능

### 코드 비교

```kotlin
// 스레드 방식: 1000개 스레드 생성 시 약 1GB 메모리 필요
for (i in 1..1000) {
    Thread {
        Thread.sleep(1000) // 스레드 블로킹
        println("Task $i completed")
    }.start()
}

// 코루틴 방식: 1000개 코루틴 생성해도 몇 MB만 사용
runBlocking {
    for (i in 1..1000) {
        launch {
            delay(1000) // 코루틴만 일시 중단, 스레드는 다른 코루틴 실행 가능
            println("Task $i completed")
        }
    }
}
```

---

## 기존 동시성 제어 방법

### 1. RDB
- **낙관적 락(Optimistic Lock)**: SW 레벨, `@Version` 어노테이션 등
- **비관적 락(Pessimistic Lock)**: RDB 레벨 DBMS Lock
- **Isolation Level 지정**:
  - `READ UNCOMMITTED`: 다른 트랜잭션 수행 중인 변경 사항을 읽을 수 있음
  - `READ COMMITTED`: 다른 트랜잭션이 커밋한 데이터만 읽을 수 있음
  - `REPEATABLE READ`: 트랜잭션 내에서 같은 데이터를 다시 읽을 때 동일한 값 유지
  - `SERIALIZABLE`: 가장 엄격한 격리 수준

### 2. Redis
- `setNx` 명령어를 사용하여 분산 락 활용
- Redis는 Single Thread 기반으로 운영되어 동시성 제어에 효과적

### 3. Spring JPA
- `@Version` Annotation 사용 → Optimistic Lock

### 4. 메시지 큐 활용
- RabbitMQ, Kafka 등을 활용하여 순차적으로 처리

---

## 참고 링크

- [동기와 비동기 with webClient (테코블)](https://tecoble.techcourse.co.kr/post/2021-07-25-async-style/)
- [Spring @Async와 스레드풀](https://velog.io/@gillog/Spring-Async-Annotation)
- [Concurrency vs Parallelism: A Simplified Explanation](https://betterprogramming.pub/concurrency-vs-parallelism-a-simplified-explanation-7e7eacc49dbd)
- [동시성 (Concurrency) vs 병렬성 (Parallelism)](https://seamless.tistory.com/42)
- [개발자 면접 질문 - 가시성, 원자성](https://velog.io/@chullll/OS-%EA%B0%80%EC%8B%9C%EC%84%B1-%EC%9B%90%EC%9E%90%EC%84%B1)
- [OS 데드락(Deadlock)은 언제 발생하고, OS, Java에서 어떻게 해결할까?](https://velog.io/@steadystudy/OS-%EB%8D%B0%EB%93%9C%EB%9D%BDDeadlock%EC%9D%80-%EC%96%B8%EC%A0%9C-%EB%B0%9C%EC%83%9D%ED%95%98%EA%B3%A0-OS-Java%EC%97%90%EC%84%9C-%EC%96%B4%EB%96%BB%EA%B2%8C-%ED%95%B4%EA%B2%B0%ED%95%A0%EA%B9%8C)
- [Baeldung - Guide to RejectedExecutionHandler](https://www.baeldung.com/java-rejectedexecutionhandler)
- [스레드풀 포화 정책(Saturation Policy) 알아보기](https://www.ssemi.net/what-is-the-saturation-policy-of-threadpool/)
- [코루틴은 왜 빠른 걸까요?](https://medium.com/@wonjun.dev/%EC%BD%94%EB%A3%A8%ED%8B%B4%EC%9D%80-%EC%99%9C-%EB%B9%A0%EB%A5%B8-%EA%B1%B8%EA%B9%8C%EC%9A%94-9c8f63d45bf3)
- [kakaopay - 코루틴과 Virtual Thread 비교와 사용](https://tech.kakaopay.com/post/coroutine-virtual-thread-use/)

> 출처: [maeil-mail.kr](https://www.maeil-mail.kr) - 질문 77, 120, 141, 203, 254(270), 293(308)
