# JVM Runtime

## JVM 구조

- **클래스 로더**: 클래스 파일 로딩
- **런타임 메모리 영역**
  - Stack: 스레드별 호출 정보/지역변수
  - Heap: 객체/배열 저장 (GC 대상)
  - Method Area: 클래스 메타데이터, static 변수, 상수 풀
  - PC Register: 현재 실행 명령 위치
  - Native Method Stack
- **실행 엔진**
  - 인터프리터
  - JIT 컴파일러
  - GC

## JVM 실행 흐름 (소스코드 → 실행)

우리가 작성한 `.java` 파일은 JDK에 포함된 **javac(Java Compiler)** 를 통해 컴파일됩니다. 이 과정에서 JVM이 이해할 수 있는 **바이트 코드**로 변환되어 `.class` 파일이 생성됩니다.

### 1단계: javac 컴파일

```
.java 파일 → (javac 컴파일) → .class 파일 (바이트 코드)
```

### 2단계: 클래스 로더 (Class Loader)

클래스 로더가 바이트 코드를 JVM 메모리에 **동적으로** 로드합니다. "동적"이란 프로그램이 시작될 때 모든 클래스를 한꺼번에 로드하는 것이 아니라, 런타임 시점에 필요한 클래스만 로드하는 것을 의미합니다.

**클래스가 로드되는 시점:**
- 인스턴스를 생성할 때
- static 메서드나 변수를 사용할 때
- static 변수에 값을 할당할 때

로드된 바이트 코드는 Method Area에 저장되며, 이때 세 단계를 거칩니다:

**로딩(Loading)**: 클래스 로더가 `.class` 파일을 읽어 JVM 메모리(Method Area)에 로드

**링킹(Linking)**: 로드된 클래스가 실행될 수 있도록 준비하는 단계
1. **Verification**: `.class` 파일이 구조적으로 올바른지 확인
2. **Preparation**: static 변수를 메모리에 할당하고 기본값으로 초기화
3. **Resolution**: 런타임 상수 풀에 있는 심볼릭 레퍼런스를 실제 메모리 레퍼런스로 교체

**초기화(Initialization)**: static 변수를 사용자가 지정한 값으로 초기화하고 static 블록을 실행

### 3단계: 실행 엔진 (Execution Engine)

로드된 바이트 코드를 실행합니다. 바이트 코드는 컴퓨터가 읽을 수 없기 때문에 **인터프리터**와 **JIT 컴파일러**를 함께 사용하여 기계어로 변환합니다.

**인터프리터 (Interpreter)**
- 바이트 코드를 한 줄씩 읽어서 실행
- 초기 실행 속도가 빠르지만, 같은 코드가 반복 실행될 경우 매번 해석해야 하므로 성능이 저하됨

**JIT 컴파일러 (Just-In-Time Compiler)**
- 자주 실행되는 메서드(Hotspot)를 감지하면 해당 메서드 전체를 **네이티브 코드**로 변환하여 캐싱
- 반복 실행 시 인터프리터보다 훨씬 빠르게 실행
- 단, JIT 컴파일 과정 자체에 시간이 소요되어 초기 실행 시 오버헤드가 발생할 수 있음

**두 방식을 함께 사용하는 이유**: 초기 실행 속도(인터프리터)와 높은 반복 실행 성능(JIT)을 동시에 달성하기 위함

### 참고 링크

- [Oracle - Loading, Linking, and Initializing](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-5.html)
- [Inpa 블로그 - 클래스는 언제 메모리에 로딩 & 초기화 되는가?](https://inpa.tistory.com/entry/JAVA-%E2%98%95-%ED%81%B4%EB%9E%98%EC%8A%A4%EB%8A%94-%EC%96%B8%EC%A0%9C-%EB%A9%94%EB%AA%A8%EB%A6%AC%EC%97%90-%EB%A1%9C%EB%94%A9-%EC%B4%88%EA%B8%B0%ED%99%94-%EB%90%98%EB%8A%94%EA%B0%80-%E2%9D%93)
- [BellSoft - Compilation in Java: JIT vs AOT](https://bell-sw.com/blog/compilation-in-java-jit-vs-aot/)

## GC 대상 판단 (Reachability Analysis)

GC(Garbage Collection)는 JVM의 힙 영역에서 동적으로 할당했던 메모리 중에서 필요 없어진 객체를 주기적으로 제거합니다.

GC는 특정 객체가 사용 중인지 아닌지 판단하기 위해 **도달 가능성(Reachability)** 개념을 사용합니다.

- 특정 객체에 대한 참조가 존재하면 → **도달 가능 (Reachable)**
- 참조가 존재하지 않으면 → **도달 불가 (Unreachable)** → GC 대상

### GC Root

힙 영역에 있는 객체에 대한 참조는 4가지 케이스가 존재합니다:
1. 힙 내부 객체 간의 참조
2. 스택 영역의 변수에 의한 참조
3. JNI에 의해 생성된 객체에 대한 참조 (네이티브 스택 영역)
4. 메서드 영역의 정적 변수에 의한 참조

이때, **힙 내부 객체 간의 참조를 제외한 나머지**를 **Root Set(GC Root)** 이라고 합니다. Root Set으로부터 시작한 참조 사슬에 속한 객체들은 도달 가능한 객체이며, 이 참조 사슬과 무관한 객체들은 GC 대상이 됩니다.

### 개발자가 GC 대상 판단에 관여하는 방법

`java.lang.ref` 패키지의 `SoftReference`, `WeakReference` 클래스를 통해 GC 대상 판단에 일정 부분 관여할 수 있습니다.

```java
Origin o = new Origin();
WeakReference<Origin> wo = new WeakReference<>(o);
```

- **SoftReference**: Root Set으로부터 참조가 없는 경우, 남아있는 힙 메모리 크기에 따라 GC 여부 결정 (메모리가 부족할 때 회수)
- **WeakReference**: Root Set으로부터 참조가 없는 경우, 즉시 GC 대상이 됨

### 참고 링크

- [네이버 D2 - Java Reference와 GC](https://d2.naver.com/helloworld/329631)

## GC 알고리즘

### Serial GC

- JDK에 도입된 최초의 가비지 컬렉터
- **단일 스레드**로 동작하는 가장 단순한 형태
- 작은 힙 메모리와 단일 CPU 환경에 적합
- Stop-The-World 시간이 가장 길게 발생

### Parallel GC

- Java 5부터 8까지 **default** 가비지 컬렉터
- Serial GC와 달리 **Young 영역의 GC를 멀티 스레드**로 수행
- 높은 처리량에 초점을 두기 때문에 **Throughput GC**라고도 불림

### Parallel Old GC

- Parallel GC의 향상된 버전
- **Old 영역에서도 멀티 스레드**를 활용하여 GC를 수행

### CMS (Concurrent Mark-Sweep) GC

- Java 5부터 8까지 사용
- 애플리케이션 스레드와 병렬로 실행되어 **Stop-The-World 시간을 최소화**하도록 설계
- 단점: 메모리와 CPU 사용량이 많고, 메모리 압축을 수행하지 않아 **메모리 단편화** 문제
- Java 9부터 deprecated, **Java 14에서 완전히 제거**

### G1 (Garbage First) GC

- Java 9부터 **default** 가비지 컬렉터
- 힙을 여러 개의 **region**으로 나누어 논리적으로 Young, Old 영역을 구분
- 처리량과 Stop-The-World 시간 사이의 균형을 유지
- **32GB보다 작은 힙 메모리**를 사용할 때 가장 효과적
- GC 대상이 많은 region을 먼저 회수하기 때문에 "Garbage First"라는 이름이 붙음

**Humongous 객체**: region 크기의 50% 이상을 차지하는 큰 객체. Young 영역을 거치지 않고 바로 Old 영역에 할당되어 Full GC 발생 가능성이 높음. `-XX:G1HeapRegionSize`로 region 크기 조정 가능.

### ZGC

- Java 11부터 도입
- **10ms 이하의 Stop-The-World 시간**과 **대용량 힙** 처리를 목표로 설계

### Shenandoah GC

- Red Hat에서 개발, Java 12부터 도입
- G1 GC와 마찬가지로 힙을 여러 개의 region으로 나누어 처리
- ZGC처럼 저지연 Stop-The-World와 대용량 힙 처리를 목표로 함

### Epsilon GC

- Java 11부터 도입
- **GC 기능이 없는 실험용** 가비지 컬렉터
- 애플리케이션 성능 테스트에서 GC 영향을 분리하거나 GC 오버헤드 없이 메모리 한계를 테스트할 때 사용
- 프로덕션 환경에는 부적합

### GC 선택 기준

OpenJDK는 **CPU 코어 수가 2개 이상이고 메모리가 2GB 이상**인 서버를 Server-Class Machine으로 인식합니다.
- Server-Class Machine → G1 GC 사용
- 그 외 (소규모 서버 등) → Serial GC 사용

G1 GC를 명시적으로 지정하려면: `-XX:+UseG1GC`

### 참고 링크

- [Naver D2 - Java Garbage Collection](https://d2.naver.com/helloworld/1329)
- [Oracle Docs Java 17 - Garbage-First (G1) Garbage Collector](https://docs.oracle.com/en/java/javase/17/gctuning/garbage-first-g1-garbage-collector1.html#GUID-ED3AB6D3-FD9B-4447-9EDF-983ED2F7A573)
