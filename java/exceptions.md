# Java Exceptions

## Error vs Exception

Error는 주로 JVM에서 발생하는 심각한 문제로, `OutOfMemoryError`, `StackOverflowError` 등 시스템 레벨에서 발생하는 오류입니다. 일반적으로 프로그램에서 처리하지 않으며, **회복이 어려운 오류**에 속하며, 애플리케이션 코드에서 복구할 수 없는 심각한 문제를 나타냅니다.

반면, Exception은 프로그램 실행 중 발생할 수 있는 오류 상황을 나타냅니다. 대부분의 경우 **회복 가능성**이 있으며, 프로그램 내에서 예외 처리를 통해 오류 상황을 제어할 수 있습니다. Exception은 다시 `Checked Exception`과 `Unchecked Exception`으로 나눌 수 있습니다.

## Checked vs Unchecked Exception

### Checked Exception

- **컴파일 시점에 확인**되며, **반드시 처리해야 하는 예외**
- 자바에서는 `IOException`, `SQLException` 등이 이에 속함
- Checked Exception을 유발하는 메서드를 호출하는 경우, 메서드 시그니처에 `throws`를 사용하여 호출자에게 예외를 위임하거나, 메서드 내에서 try-catch를 사용하여 반드시 처리해야 함
- **사용 시점**: 외부 환경과의 상호작용에서 발생할 가능성이 높은 예외에 적합 (파일 입출력, 네트워크 통신 등)
- Spring 트랜잭션과의 관계: Spring의 `@Transactional`은 기본적으로 **Unchecked Exception**에만 롤백을 적용하며, Checked Exception은 롤백하지 않음. 필요 시 `rollbackFor = Exception.class`로 명시해야 함

### Unchecked Exception

- **런타임 시점에 발생**하는 예외로, 컴파일러가 처리 여부를 강제하지 않음
- 자바에서는 `RuntimeException`을 상속한 예외들이 해당됨 (`NullPointerException`, `ArrayIndexOutOfBoundsException`, `IllegalArgumentException` 등)
- 일반적으로 프로그래머의 실수나 코드 오류로 인해 발생
- **사용 시점**: 코드 오류, 논리적 결함 등 프로그래머의 실수로 인해 발생할 수 있는 예외에 적합. null 참조 또는 잘못된 인덱스 접근 등은 호출자가 미리 예측하거나 처리할 수 없기 때문에 Unchecked Exception으로 두는 것이 좋음

### 예외 계층 구조

```
Throwable
├── Error (복구 불가)
│   ├── OutOfMemoryError
│   └── StackOverflowError
└── Exception (복구 가능)
    ├── IOException        ← Checked
    ├── SQLException       ← Checked
    └── RuntimeException   ← Unchecked
        ├── NullPointerException
        ├── IllegalArgumentException
        └── ArrayIndexOutOfBoundsException
```

## try-with-resources

커넥션, 입출력 스트림과 같은 자원을 사용한 후에는 자원을 해제해서 성능 문제, 메모리 누수 등을 방지해야 합니다. **try-with-resources**는 이러한 자원을 자동으로 해제하는 기능으로, Java 7부터 도입되었습니다.

try-with-resources가 정상적으로 동작하려면 `AutoCloseable` 인터페이스를 구현한 객체를 사용해야 하고, `try()` 괄호 내에서 변수를 선언해야 합니다.

```java
try (BufferedReader br = new BufferedReader(new FileReader("path"))) {
    return br.readLine();
} catch (IOException e) {
    return null;
}
```

### try-catch-finally 대신 try-with-resources를 사용해야 하는 이유

`try-catch-finally`는 finally 블록에서 `close()`를 명시적으로 호출해야 합니다. 하지만 `close()` 호출을 누락하거나 이 과정에서 또 다른 예외가 발생하면 예외 처리가 복잡해지는 문제가 있습니다.

또한 여러 개의 자원을 다룰 경우, 먼저 `close()`를 호출한 자원에서 에러가 발생하면 다음에 `close()`를 호출한 자원은 해제되지 않습니다.

**try-with-resources를 사용하면:**
- try 블록이 종료될 때 `close()`를 자동으로 호출하여 자원을 해제
- finally 블록 없이도 자원을 안전하게 정리하기 때문에 코드가 간결해짐
- try 문에서 여러 자원을 선언하면, 선언된 **반대 순서**로 자동 해제

### Suppressed Exception (억제된 예외)

**Suppressed Exception**은 예외가 발생했지만 무시되는 예외를 의미합니다. try-with-resources는 `close()` 과정에서 발생한 예외를 Suppressed Exception으로 관리합니다.

```java
class CustomResource implements AutoCloseable {
    @Override
    public void close() throws Exception {
        throw new Exception("Close Exception 발생");
    }

    void process() throws Exception {
        throw new Exception("Primary Exception 발생");
    }
}
```

- **try-with-resources**: 원본 예외(Primary Exception)를 유지하면서 close()에서 발생한 예외를 Suppressed로 추적
- **try-catch-finally**: `close()` 호출 시 예외가 발생하면 원래 예외가 사라지고 `close()`에서 발생한 예외만 남을 수 있음

## 참고 링크

- [기억보단 기록을 - 좋은 예외(Exception) 처리](https://jojoldu.tistory.com/734)
- [custom exception을 언제 써야 할까?](https://tecoble.techcourse.co.kr/post/2020-08-17-custom-exception/)
