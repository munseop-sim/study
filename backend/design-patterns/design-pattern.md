# 디자인 패턴

크게 생성, 구조, 행동으로 구분할 수 있다.

## 생성 (Creational)
- Builder
- Factory
- Singleton

## 행동 (Behavioral)
- Observer
- Strategy
- Template Method

## 구조 (Structural)
- Decorator
- Proxy
- Adapter
- Facade

---

## 전략 패턴 (Strategy Pattern)

**전략 패턴(Strategy Pattern)** 은 객체의 행위를 동적으로 변경하고 싶은 경우, 코드를 직접 수정하는 것이 아닌 추상화된 전략의 구현만을 바꿔 객체의 행위를 변경하는 디자인 패턴입니다.

자바 언어의 요소와 함께 설명하자면, 객체의 행위를 Interface로 정의하고, Interface의 메서드를 구현하는 구현체들을 주입하는 것이 전략 패턴의 대표적인 형태입니다.

```java
class Car {

    private final MoveStrategy strategy;
    private final int position;

    public Car(MoveStrategy strategy, int position) {
        this.strategy = strategy;
        this.position = position;
    }

    public Car move(int input) {
        if (strategy.isMovable(input)) {
            return new Car(strategy, position + 1);
        }
        return this;
    }
}

interface MoveStrategy {
    boolean isMovable(int input);
}

class EvenNumberMoveStrategy implements MoveStrategy {

    @Override
    public boolean isMovable(int input) {
        return (input % 2) == 0;
    }
}

class OddNumberMoveStrategy implements MoveStrategy { ... }

class PrimeNumberMoveStrategy implements MoveStrategy { ... }
```

- MoveStrategy 타입 필드를 선언하고 외부에서 이를 구현한 전략을 주입받도록 구현하면 유연하게 자동차의 움직임 전략을 교체할 수 있습니다.
- SOLID 원칙 중 OCP(개방 폐쇄 원칙)와 DIP(의존성 역전 원칙)를 준수하는 데 용이합니다.

참고: [maeil-mail: 전략 패턴에 대해서 설명해주세요](https://www.maeil-mail.kr/question/110)

---

## CQRS 패턴 (Command Query Responsibility Segregation)

시스템은 크게 상태 변경과 조회 기능을 제공합니다. **명령 쿼리 책임 분리 패턴(CQRS)** 은 상태를 변경하기 위한 명령(Command)을 위한 모델과 상태를 제공하는 조회(Query)를 위한 모델을 분리하는 패턴입니다.

예를 들어, `Order`라는 리소스를 `Order`(명령용), `OrderData`(조회용) 2개의 모델로 나누어서 관리할 수 있습니다. `OrderData`를 이용해서 표현 계층에 데이터를 출력하는 데 사용하고, 애플리케이션에서는 `Order`를 활용해 변경을 수행할 수 있습니다.

### 장점

- 소프트웨어의 유지보수성을 높일 수 있습니다.
- 모델별로 성능이나 요구사항에 맞는 데이터베이스나 데이터 접근 기술을 사용할 수 있습니다.
  - 명령 모델: 트랜잭션이 지원되는 RDB + JPA
  - 조회 모델: 조회 성능이 높은 NoSQL + MyBatis

### 단점

- 구현 코드가 많고, 더 많은 구현 기술이 필요합니다.
- 명령 모델의 변경을 조회 모델로 전파하여 동기화시켜야 할 필요가 있을 수 있습니다.
- 단일 모델을 사용할 때 발생하는 복잡함과 조회 전용 모델을 만들 때 발생하는 복잡함을 비교해서 신중하게 도입을 결정해야 합니다.

참고: [maeil-mail: CQRS 패턴이란 무엇인가요?](https://www.maeil-mail.kr/question/121)

---

## 널 오브젝트 패턴 (Null Object Pattern)

**널 오브젝트 패턴(Null Object Pattern)** 이란 객체가 존재하지 않을 때, 널을 전달하는 것이 아닌 아무 일도 하지 않는 객체를 전달하는 기법입니다.

```java
// 일반적인 널 체크 코드 (반복 발생 시 복잡해짐)
public void doSomething(MyObject obj) {
    if (obj == null) {
        throw new Exception();
    }
    obj.doMethod();
}
```

```java
// 널 오브젝트 패턴 적용
class MyNullObject implements MyObject {

    @Override
    public void doMethod() {
        // 아무것도 하지 않음
    }
}

class MyRealObject implements MyObject {

    @Override
    public void doMethod() {
        System.out.println("무엇인가 수행합니다.");
    }
}

public void doSomething(MyObject obj) {
    obj.doMethod(); // 널 체크 불필요
}
```

- 값이 널인 경우에만 사용되는 것이 아닌 특별한 케이스에서 모두 응용할 수 있습니다. 가령, 스택 자료구조를 만들 때 용량이 0인 경우 `ZeroCapacityStack`을 만들 수 있습니다.

### 장단점

- **장점**: 반복적인 널 체크 코드를 간소화하고 협력을 재사용하는 데 용이합니다.
- **단점**: 오히려 예외를 탐지하기 어려운 상황을 만들 수 있습니다.

참고:
- [maeil-mail: 널 오브젝트 패턴이란 무엇인가요?](https://www.maeil-mail.kr/question/187)
- [기계인간 John Grib - 널 오브젝트 패턴](https://johngrib.github.io/wiki/pattern/null-object/)
- [마틴파울러 닷컴 - Special Case](https://martinfowler.com/eaaCatalog/specialCase.html)

---

## 템플릿 메서드 패턴 (Template Method Pattern)

**템플릿 메서드 패턴(Template Method Pattern)** 은 기능의 뼈대와 구현을 분리하는 행위 디자인 패턴입니다. 실행 단계의 절차를 결정하는 상위 클래스와 실행 단계를 구현하는 하위 클래스로 구성됩니다.

```java
public abstract class Student {

    public abstract void study();
    public abstract void watchYoutube();
    public abstract void sleep();

    // 템플릿 메서드 - 알고리즘 골격을 정의
    final public void doDailyRoutine() {
        study();
        watchYoutube();
        sleep();
    }
}

class BackendStudent extends Student {

    @Override
    public void study() {
        System.out.println("영한님 JPA 강의를 수강합니다.");
    }

    @Override
    public void watchYoutube() {
        System.out.println("개발바닥 유튜브를 시청합니다.");
    }

    @Override
    public void sleep() {
        System.out.println("7시간 잠을 잡니다.");
    }
}
```

### 장단점

- **장점**: 공통 로직을 상위 클래스에 모아 중복 코드를 줄일 수 있으며, 코드의 재사용성을 높일 수 있습니다.
- **단점**:
  - 하위 클래스를 개발할 때 상위 클래스의 내용을 알기 전까지 어떠한 방식으로 동작할지 예측하기 어렵습니다.
  - 상위 클래스 수정이 발생하는 경우 모든 하위 클래스를 변경해야 합니다.

참고:
- [maeil-mail: 템플릿 메서드 패턴이란 무엇인가요?](https://www.maeil-mail.kr/question/258)
- [라인 기술 블로그 - 템플릿 메서드 패턴으로 모순 없는 상태 보장하기](https://engineering.linecorp.com/ko/blog/templete-method-pattern)
- [Refactoring GURU - 템플릿 메서드 패턴](https://refactoring.guru/ko/design-patterns/template-method)

---

## 싱글턴 패턴 (Singleton Pattern)

**싱글턴 패턴(Singleton Pattern)** 이란 생성자를 여러 차례 호출해도 실제로 생성되는 객체를 하나로 유지하는 것을 의미합니다. 객체가 최초로 생성된 이후에 생성자나 객체 생성 메서드는 기존에 만들어진 객체를 반환합니다.

### 기본 구현 (Eager Initialization)

```java
public class Singleton {

    private static final Singleton INSTANCE = new Singleton();

    // 생성자 호출 제한
    private Singleton() { ... }

    public static Singleton getInstance() {
        return INSTANCE;
    }
}
```

### Thread-Safe 구현 방법

#### 1. synchronized 사용 (DCL - Double-Checked Locking)

```java
public class Singleton {

    private static volatile Singleton INSTANCE;

    private Singleton() { }

    public static Singleton getInstance() {
        if (INSTANCE == null) {
            synchronized (Singleton.class) {
                if (INSTANCE == null) {
                    INSTANCE = new Singleton();
                }
            }
        }
        return INSTANCE;
    }
}
```

#### 2. 정적 내부 클래스 (Bill Pugh Solution) - 권장 방법

```java
public class Singleton {

    private Singleton() { }

    private static class SingletonHolder {
        private static final Singleton INSTANCE = new Singleton();
    }

    public static Singleton getInstance() {
        return SingletonHolder.INSTANCE;
    }
}
```

- JVM의 클래스 로딩 메커니즘을 이용하여 Thread-safe를 보장합니다.
- `getInstance()`가 호출될 때 `SingletonHolder` 클래스가 로딩되면서 인스턴스가 생성됩니다.

#### 3. Enum 사용 (이펙티브 자바 권장)

```java
public enum Singleton {
    INSTANCE;

    public void doSomething() { ... }
}
```

- 직렬화와 리플렉션에 의한 싱글턴 위반도 방지합니다.

### 장단점

- **장점**:
  - 하나의 객체를 여러 상황에서 재사용할 수 있기 때문에 메모리 낭비를 방지할 수 있습니다.
  - 여러 다른 객체가 하나의 인스턴스에 쉽게 접근할 수 있어 편리합니다.
- **단점**:
  - 전역 객체를 생성한다는 특성상 코드의 복잡도를 높이고, 테스트하기 어려운 코드를 만들 수 있습니다.
  - 지연 초기화(lazy initialization) 시 여러 스레드가 동시에 생성자에 접근하면 두 개 이상의 객체가 생성될 수 있으므로 동시성 문제를 고려해야 합니다.
  - 테스트에서는 싱글턴 객체의 상태를 초기화하는 과정이 필요합니다.
  - 싱글턴 객체가 인터페이스를 구현하지 않은 경우, 테스트 환경에서 가짜 구현체로 대체하여 주입하기 어렵습니다.

참고:
- [maeil-mail: 싱글턴 패턴이란 무엇인가요?](https://www.maeil-mail.kr/question/259)
- [테코블 - 싱글톤(Singleton) 패턴이란?](https://tecoble.techcourse.co.kr/post/2020-11-07-singleton/)
- [싱글톤(SingleTon) 패턴 구현방법 6가지, Bill Pugh Solution](https://haon.blog/java/singleton/)
- [이펙티브 자바 - private 생성자나 열거 타입으로 싱글턴임을 보증하라](https://incheol-jung.gitbook.io/docs/study/effective-java/undefined/2020-03-20-effective-3item)
