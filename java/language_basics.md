# Java Language Basics

## `main` 메서드가 `static`인 이유

- 객체 생성 없이 JVM이 진입점을 바로 호출해야 하기 때문
- 클래스 기준 호출 방식(`ClassName.main(args)`)과 맞음

## 오버로딩 vs 오버라이딩

- 오버로딩: 같은 이름, 다른 시그니처 (컴파일 시점 결정)
- 오버라이딩: 상위 타입 메서드 재정의 (런타임 시점 결정, 다형성)

## equals/hashCode

컬렉션(`HashMap`, `HashSet`) 일관성을 위해 함께 재정의 필요.

해시값을 사용하는 자료구조는 hashCode 메서드의 반환값을 사용하는데, **hashCode 반환값이 일치한 이후 equals 반환값이 true일 때만** 논리적으로 같은 객체라고 판단합니다.

hashCode를 재정의하지 않으면 Object 클래스의 기본 hashCode(객체 고유 주소 기반)를 사용하므로, 논리적으로 같은 두 객체가 HashSet에서 중복 처리되지 않는 문제가 발생합니다.

```java
// equals만 정의하면 HashSet이 제대로 동작하지 않는다.
Subscribe subscribe1 = new Subscribe("maeil@gmail.com", "backend");
Subscribe subscribe2 = new Subscribe("maeil@gmail.com", "backend");
HashSet<Subscribe> subscribes = new HashSet<>(List.of(subscribe1, subscribe2));
System.out.println(subscribes.size()); // 2 (기대값은 1)
```

## 동등성 vs 동일성

- **동등성(Equality)**: 논리적으로 객체의 내용이 같은지 비교 → `equals()` 사용
- **동일성(Identity)**: 두 객체가 메모리 상에서 같은 객체인지 비교 → `==` 연산자 사용

```java
Apple apple1 = new Apple(100);
Apple apple2 = new Apple(100);
Apple apple3 = apple1;

System.out.println(apple1.equals(apple2)); // true (동등성)
System.out.println(apple1 == apple2);      // false (동일성)
System.out.println(apple1 == apple3);      // true (동일성)
```

String 리터럴은 **String Constant Pool**에 저장되어 동일한 문자열 리터럴끼리 `==`이 true가 될 수 있지만, `new String()`으로 생성하면 항상 새로운 객체가 생성되어 false가 됩니다. 따라서 문자열 비교 시 항상 `equals()`를 사용해야 합니다.

## 일급 컬렉션 (First-Class Collection)

하나의 컬렉션을 감싸는 클래스를 만들고, 해당 클래스에서 컬렉션과 관련된 비즈니스 로직을 관리하는 패턴입니다.

```java
public class Orders {
    private final List<Order> orders;

    public Orders(List<Order> orders) {
        validate(orders); // 검증 수행
        this.orders = new ArrayList<>(orders);
    }

    public void add(Order order) {
        if (order == null) {
            throw new IllegalArgumentException("Order cannot be null");
        }
        orders.add(order);
    }

    public List<Order> getAll() {
        return Collections.unmodifiableList(orders);
    }

    public double getTotalAmount() {
        return orders.stream()
                     .mapToDouble(Order::getAmount)
                     .sum();
    }
}
```

### 일급 컬렉션을 사용해야 하는 이유

- 컬렉션과 관련된 비즈니스 로직을 한 곳에 모아 관리할 수 있음
- 비즈니스에 특화된 명확한 이름을 부여할 수 있음 (`List<Order>` → `Orders`)
- 불필요한 컬렉션 API를 외부로 노출하지 않도록 제한 가능
- `Collections.unmodifiableList()` 등을 사용하여 불변성을 보장하고 예기치 않은 변경으로부터 데이터를 보호

### 참고 링크

- [기억보단 기록을 - 일급 컬렉션의 소개와 써야할 이유](https://jojoldu.tistory.com/412)
- [이유와 솔루션으로 정리하는 객체지향 생활체조 원칙](https://hudi.blog/thoughtworks-anthology-object-calisthenics/)

## 얕은 복사 vs 깊은 복사

객체를 복사할 때 두 가지 방식이 있습니다.

### 얕은 복사 (Shallow Copy)

새로운 객체를 만들지만, 내부의 참조 타입 필드는 **원본과 동일한 객체를 참조**합니다.

```java
public Book shallowCopy() {
    return new Book(this.name, this.author); // author는 같은 객체 참조
}
```

- `shallowCopyBook`의 author를 변경하면 원본 `book`의 author도 변경됨
- 두 객체가 같은 Author 객체를 공유하기 때문

### 깊은 복사 (Deep Copy)

객체와 내부의 참조 타입 필드 모두 **새로운 객체로 생성**합니다.

```java
public Book deepCopy() {
    Author copiedAuthor = new Author(this.author.getName()); // 새로운 Author 생성
    return new Book(this.name, copiedAuthor);
}
```

- `deepCopyBook`의 author를 변경해도 원본 `book`의 author는 변경되지 않음
- 서로 다른 Author 객체를 참조하기 때문

## Call by Value vs Call by Reference (Java에서의 동작)

메서드를 호출할 때 인자를 전달하는 방법:
- **Call by Value**: 값 자체를 복사하여 넘기는 방식. 호출된 함수의 파라미터가 변경되어도 원본에 영향 없음
- **Call by Reference**: 참조를 직접 전달하는 방식. 파라미터 수정이 원본에 영향을 미침

### 자바는 항상 Call by Value

자바의 변수는 스택 영역에 할당됩니다.
- **원시 타입**: 값 자체가 스택에 저장됨. 메서드에 전달 시 값이 복사되므로 원본에 영향 없음
- **참조 타입**: 객체 자체는 힙에 저장되고, 스택의 변수가 객체의 주소를 가짐. 메서드 호출 시 스택 프레임에 주소값이 복사되어 전달됨

```java
public void foo() {
    Student student = new Student();
    var(student); // student 주소값의 복사본이 전달됨
}

public void var(Student student) {
    student.study(); // 같은 객체를 참조하므로 원본에 영향 가능
    student = new Student(); // 이 재할당은 원본에 영향 없음
}
```

참조 타입을 전달할 때 내부 상태를 변경하면 원본에 영향을 미치지만, 이는 **주소값의 복사본**을 전달한 것이므로 여전히 Call by Value입니다.

### 참고 링크

- [코드라떼 - 자바 메모리 모델 기초](https://www.codelatte.io/courses/java_programming_basic/IGO4YNAECLV8MSVF)
- [코드라떼 - Java == Call By Value](https://www.codelatte.io/courses/java_programming_basic/R842DJSB0BTCRLKM)

## 방어적 복사 (Defensive Copy)

**방어적 복사**는 원본과의 참조를 끊은 복사본을 만들어 사용하는 방식으로, 원본의 변경에 의한 예상치 못한 사이드 이펙트를 방지합니다.

### 방어적 복사의 두 가지 시점

1. **생성자에서**: 인자로 받은 객체의 복사본을 만들어 내부 필드를 초기화

```java
public Lotto(List<LottoNumber> numbers) {
    validateSize(numbers);
    this.numbers = new ArrayList<>(numbers); // 방어적 복사
}
```

2. **getter에서**: 객체를 반환할 때 복사본을 반환

```java
public List<LottoNumber> getNumbers() {
    return Collections.unmodifiableList(numbers); // Unmodifiable Collection 반환
}
```

### 주의사항

방어적 복사는 **얕은 복사**이기 때문에 내부 요소까지는 보호하지 못합니다.

```java
Lotto lotto = new Lotto(numbers);
numbers.get(0).changeNumber(1); // Lotto 내부의 LottoNumber가 변경될 수 있음
```

또한 검증 이후에 방어적 복사가 이루어지면, 검증 통과 후 외부에서 컬렉션이 변경될 수 있으므로 **방어적 복사를 먼저 수행한 후 검증**하는 것이 안전합니다.

### 참고 링크

- [테코블 - 방어적 복사와 Unmodifiable Collection](https://tecoble.techcourse.co.kr/post/2021-04-26-defensive-copy-vs-unmodifiable/)

## 함수형 프로그래밍 (Functional Programming)

**함수형 프로그래밍**은 객체지향 패러다임과 마찬가지로 하나의 프로그래밍 패러다임입니다.

- 객체지향 프로그래밍: 움직이는 부분을 **캡슐화**하여 코드의 이해를 도움
- 함수형 프로그래밍: 움직이는 부분을 **최소화**하여 코드 이해를 도움
- 이 둘은 상충하는 개념이 아니며, 함께 조화되어 사용될 수 있음

### 핵심 개념

**부수 효과(Side Effect)**: 값을 반환하는 것 이외에 부수적으로 발생하는 일들 (변수 수정, I/O 작업 등). 함수형 프로그래밍은 부수 효과를 추상화하고 분리하여 코드를 이해하기 쉽게 만듭니다.

**순수 함수(Pure Function)**: 같은 입력이 들어오면 항상 같은 값을 반환하고, 부수효과를 일으키지 않는 함수. 함수 합성은 순수 함수로 이루어집니다.

**함수 합성(Function Composition)**: 특정 함수의 공역이 다른 함수의 정의역과 일치하는 경우, 두 함수를 이어서 새로운 함수를 만드는 연산. 프로그래밍에서는 A 함수가 int를 반환하고 B 함수가 int를 인자로 받는다면 `B(A())`와 같은 형태로 호출하는 것을 말합니다.

**불변성(Immutability)**: 데이터를 변경하지 않고 새로운 데이터를 생성하는 방식. 사이드 이펙트를 줄이고 예측 가능한 코드를 작성할 수 있습니다.

### Java에서의 함수형 프로그래밍

```java
// 순수 함수를 합성하여 sum과 factorial을 구현
private int sum() {
    return loop((a, b) -> a + b, 0, range(1, 100));
}

private int factorial(int n) {
    return loop((a, b) -> a * b, 1, range(1, n));
}

private int loop(BiFunction<Integer, Integer, Integer> fn, int sum, Queue<Integer> queue) {
    if (queue.isEmpty()) return sum;
    return loop(fn, fn.apply(sum, queue.poll()), queue);
}
```

**람다(Lambda)**: 익명 함수를 간결하게 표현하는 문법으로, Java 8부터 도입.

**스트림(Stream)**: 컬렉션 데이터를 함수형 스타일로 처리하는 API. `filter`, `map`, `reduce` 등의 고차 함수를 제공합니다.

### 참고 링크

- [So You Want to be a Functional Programmer (Part 1)](https://cscalfani.medium.com/so-you-want-to-be-a-functional-programmer-part-1-1f15e387e536)
- [So You Want to be a Functional Programmer (Part 2)](https://cscalfani.medium.com/so-you-want-to-be-a-functional-programmer-part-2-7005682cec4a)

## Reflection

자바에서 클래스 정보를 가져오기 위해서 **Reflection API**를 사용할 수 있습니다. `java.lang.reflect` 패키지에서 제공하는 클래스를 사용하면, JVM에 로딩되어 있는 클래스와 메서드의 정보를 읽어올 수 있습니다.

대표적으로 `Class`, `Method`, `Field` 클래스가 존재합니다.

### 활용 사례

Reflection API를 사용하면 **구체적인 클래스의 타입을 몰라도** 클래스의 정보에 접근할 수 있습니다.

- 인스턴스를 감싸는 프록시 생성
- 사용자로부터 전달된 값을 처리할 메서드를 유연하게 선택
- 프레임워크/라이브러리 개발 (Spring의 DI, JPA 등이 내부적으로 사용)

프레임워크나 라이브러리 개발자는 사용자가 작성한 클래스에 대한 정보를 사전에 알 수 없기 때문에, Reflection을 통해 런타임에 동적으로 처리합니다.

### 단점 및 주의사항

- 일반적인 코드보다 복잡한 코드가 필요할 수 있음
- **캡슐화 약화**: private 필드/메서드에도 접근 가능하여 강결합으로 이어질 수 있음
- **성능 저하**: JIT 최적화가 어려워질 수 있어 일반적인 메서드 호출보다 성능이 저하될 가능성 있음 (JVM 버전과 상황에 따라 다름)

### 참고 링크

- [테코블 - Reflection API 간단히 알아보자.](https://tecoble.techcourse.co.kr/post/2020-07-16-reflection-api/)
- [Oracle Java Magazine - Reflection for the modern Java programmer](https://blogs.oracle.com/javamagazine/post/java-reflection-introduction)
- [Oracle Java Magazine - The performance implications of Java reflection](https://blogs.oracle.com/javamagazine/post/java-reflection-performance)

## String 불변성

String 객체는 **불변(Immutable)** 입니다. String 클래스는 내부적으로 `final` 키워드가 선언된 `byte[]` 필드를 사용해서 문자열을 저장합니다. `concat()`, `replace()`, `toUpperCase()`와 같은 메서드를 호출하면 기존 객체를 수정하는 것이 아니라 새로운 String 객체를 반환합니다.

### String을 불변으로 설계한 이유

1. **String Constant Pool 활용**: 동일한 문자열의 String 변수들이 같은 객체를 공유하여 메모리를 효율적으로 사용
2. **Thread-safe**: 멀티 스레드 환경에서 동기화를 신경 쓸 필요 없음
3. **해시코드 캐싱**: 해시코드를 한 번만 계산하고 재사용할 수 있음
4. **보안**: 비밀번호, 토큰, URL 등의 민감한 정보를 안전하게 다룰 수 있음

### String Constant Pool

```java
String first = "hello";         // String Constant Pool에 저장
String second = new String("hello"); // Heap에 새 객체 생성
String third = "hello";         // Constant Pool의 기존 객체 재사용

System.out.println(first == third);  // true (같은 Pool 객체)
System.out.println(first == second); // false (다른 객체)
System.out.println(first.equals(second)); // true (값은 같음)
```

`intern()` 메서드를 사용하면 Heap 영역의 String 객체를 String Constant Pool에 등록할 수 있습니다.

### 참고 링크

- [Baeldung - Why String Is Immutable in Java?](https://www.baeldung.com/java-string-immutable)
- [Baeldung - Guide to Java String Pool](https://www.baeldung.com/java-string-pool)

## Object to String 캐스팅 vs String.valueOf()

두 방식 모두 String 타입으로 변환하지만, 동작 방식과 예외 처리에서 차이가 있습니다.

### (String) value 타입 캐스팅

- value가 String 타입이 아닌 경우 `ClassCastException` 발생
- value가 null인 경우 그대로 null을 반환하여 이후 메서드 호출 시 `NullPointerException` 발생
- 타입이 확실할 때만 사용해야 함

```java
Object intValue = 10;
String str1 = (String) intValue; // ClassCastException

Object nullValue = null;
String str2 = (String) nullValue; // null
str2.concat("maeilmail"); // NullPointerException
```

### String.valueOf(value)

- value가 String 타입이 아닌 경우 `value.toString()`을 호출하여 String으로 변환
- value가 null인 경우 `"null"` 문자열을 반환 (주의: null과 "null"은 다른 의미)

```java
Object intValue = 10;
String str1 = String.valueOf(intValue); // "10"

Object nullValue = null;
String str2 = String.valueOf(nullValue); // "null"
str2.concat("maeilmail"); // "nullmaeilmail"
```

> **주의**: `String.valueOf(null)`이 `"null"`을 반환하는 것은 JSON 변환이나 DB 저장 시 문제가 될 수 있습니다. null 여부를 미리 검증하거나, `Objects.toString(value, defaultStr)`을 사용하는 것이 좋습니다.

### ClassCastException 방지: instanceof 활용

```java
Object intValue = 10;
if (intValue instanceof String str) {
    System.out.println(str);
} else {
    // 다른 처리
}
```

## 제네릭 공변/반공변/무공변

### 무공변 (Invariant) - 기본

자바에서 제네릭은 기본적으로 **무공변**입니다. 타입이 정확히 일치하지 않으면 컴파일 에러가 발생합니다.

```java
List<Animal> animals = new ArrayList<Cat>(); // 컴파일 에러
List<Cat> cats = new ArrayList<Animal>();    // 컴파일 에러
```

무공변은 타입 안정성을 보장하지만 유연성이 부족하여, 자바에서는 와일드카드(`?`)와 `extends`, `super` 키워드로 공변과 반공변을 지원합니다.

### 공변 (Covariant) - `<? extends T>`

S가 T의 하위 타입일 때 `List<S>`를 `List<? extends T>`로 사용할 수 있습니다. **읽기 전용**으로 사용되며, 쓰기는 null만 가능합니다.

```java
public void produce(List<? extends Animal> animals) {
    for (Animal a : animals) { // 읽기 가능
        System.out.println(a);
    }
    // animals.add(new Cat()); // 컴파일 에러 (쓰기 불가)
}
```

### 반공변 (Contravariant) - `<? super S>`

S가 T의 하위 타입일 때 `List<T>`를 `List<? super S>`로 사용할 수 있습니다. **쓰기 전용**으로 사용되며, 읽기는 Object 타입으로만 가능합니다.

```java
public void consume(List<? super Cat> cats) {
    cats.add(new Cat()); // 쓰기 가능
    // Cat c = cats.get(0); // 컴파일 에러 (Object로만 읽기 가능)
}
```

### PECS (Producer Extends, Consumer Super)

제네릭에서 와일드카드 상/하위 경계를 설정하는 가이드라인:
- 객체를 **생산(읽기)**할 때 → `<? extends T>`
- 객체를 **소비(쓰기)**할 때 → `<? super T>`

### `<?>` vs `<Object>` 차이점

- `<?>`: 모든 타입을 메서드 인자로 받을 수 있지만, null 외에는 추가 불가 (**읽기 전용**)
- `<Object>`: `<Object>` 외의 타입을 인자로 받을 수 없지만, 모든 객체를 추가 가능 (**읽기+쓰기**)

### 참고 링크

- [Wikipedia - 공변성과 반공변성 (컴퓨터 과학)](https://ko.wikipedia.org/wiki/%EA%B3%B5%EB%B3%80%EC%84%B1%EA%B3%BC_%EB%B0%98%EA%B3%B5%EB%B3%80%EC%84%B1_(%EC%BB%B4%ED%93%A8%ED%84%B0_%EA%B3%BC%ED%95%99))
- [Inpa 블로그 - 자바 제네릭의 공변성 & 와일드카드 완벽 이해](https://inpa.tistory.com/entry/JAVA-%E2%98%95-%EC%A0%9C%EB%84%A4%EB%A6%AD-%EC%99%80%EC%9D%BC%EB%93%9C-%EC%B9%B4%EB%93%9C-extends-super-T-%EC%99%84%EB%B2%BD-%EC%9D%B4%ED%95%B4)
- [Baeldung - Java Generics PECS](https://www.baeldung.com/java-generics-pecs)

## JCF 초기 용량 (Initial Capacity)

JCF에서 가변 크기의 자료구조를 사용할 때 초기 용량을 설정하면 리사이징을 줄이고 메모리와 연산 비용을 절약할 수 있습니다.

### ArrayList 초기 용량

- 기본 용량(capacity): **10**
- 용량이 가득 차면 기존 크기의 **1.5배** (`oldCapacity + (oldCapacity >> 1)`)로 증가
- MAX = 5,000,000일 때:
  - 기본 설정: 여러 번 리사이징 발생 → 최종 capacity 6,153,400, 약 **70MB** 사용
  - `new ArrayList(MAX)`: 불필요한 리사이징 없이 고정 → 약 **20MB** 사용

```java
List<String> arr = new ArrayList<>(MAX); // 초기 용량 지정
```

### HashMap 로드 팩터와 임계점

- **로드 팩터(load factor)**: 특정 크기의 자료구조에 데이터가 어느 정도 적재되었는지 나타내는 비율
- HashMap 초기 사이즈: **16**, 기본 로드 팩터: **0.75**
- **임계점(Threshold)** = `capacity × load factor` = `16 × 0.75` = **12**
- 내부 배열 사이즈가 12를 넘으면 배열 크기를 2배 늘리고 **재해싱(Rehashing)** 수행

초기 용량을 지정하면 불필요한 재해싱을 줄일 수 있습니다.

### 참고 링크

- [Naver D2 - Java HashMap은 어떻게 동작하는가?](https://d2.naver.com/helloworld/831311)
