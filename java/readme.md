### 1. JVM구조 설명
- Stack 영역과 NonStack 영역으로 나눠짐
- NonStack 영역은 Heap, 메소드영역 등으로 나눠짐
- Heap영역이 GC의 대상이 됨
- Stack 영역은 스레드마다 독립적으로 생성

### 2. 자바에서 메인메서드가 `static`인 이유
1. 객체 생성 없이 호출 가능해야 함
   - 자바 프로그램은 실행 시 main() 메서드부터 시작합니다.
   - main() 메서드가 호출되기 전에 클래스의 객체를 생성할 수 없습니다. 따라서 main() 메서드는 객체를 생성하지 않고도 호출할 수 있도록 static으로 선언되어야 합니다.
2. 프로그램 진입점으로 작동
   - main() 메서드는 프로그램의 진입점(entry point)입니다. 이를 위해 JVM은 main(String[] args) 메서드를 정적으로 호출합니다. 만약 static이 없다면 객체를 생성해야 호출이 가능하므로, 프로그램 실행에 어려움이 생깁니다.
3. JVM 호출 방식과 관련
   - JVM은 클래스 이름만으로 main() 메서드를 찾아 실행합니다. 이 과정에서 특정 객체를 생성하지 않고 ClassName.main(args) 형식으로 정적 메서드를 호출합니다. 이 규칙에 따라 main()은 반드시 static이어야 합니다.
4. 메모리 효율성
   - static 메서드는 클래스 로드 시 메모리에 한 번만 올라가기 때문에 메모리 사용이 효율적입니다. 프로그램이 실행되자마자 호출되는 main() 메서드는 정적 메서드로 만들어 효율적으로 처리합니다.


### 3. 자바 스레드
- 스레드 구현방법
  - `Thread` 클래스 상속
  - `Runnable` 인터페이스 구현
- `Future`: 작업이 완료될 때 결과를 가져올 수 있는 객체이지만, 결과를 기다릴 때 블로킹이 발생할 수 있음.
- `CompletableFuture`: 비동기 작업의 완료를 기다리지 않고도, 콜백을 통해 결과를 처리할 수 있도록 더 유연한 API 제공 
- `ExecutorService`
  - 스레드의 생성/시작/종료 등 스레드의 생명주기를 관리하는 인터페이스
  - 스레드 풀 관리
  - 비동기 작업 실행가능
- 동시성관리
  - Atomic 타입 객체 사용(Thread safe)
  - synchronized 키워드 사용
  - volatile 키워드 사용 : 모든 스레드에 변수의 변경사항이 즉시 보임
    - 변수에 접근하는 모든 스레드가 자신의 메인 메모리가 아닌 공유 메모리에서 직접 변수 값을 읽고 쓴다는 것을 의미
    - 변수의 읽기/쓰기 작업이 원자적으로 수행
  - Lock 인터페이스

### 4. 오버로딩 오버라이딩
- overloading: 동일한 이름의 메소드를 입력파라미터 or 리턴타입을 다르게 해서 작성 가능
- overriding: 부모의 함수 또는 프로퍼티를 재정의

### equals 와 hashCode를 함께 override해야하는 이유?
- equals는 객체의 값의 동등성을 비교하기위해 override한다. hashCode를 함께 override hashMap, hashSet과 같이 hash값을 사용하는 경우에 자바의 기본인 메모리주소값이 참조되므로 불일치가 발생할 수 있다. 따라서 기본적으로 equals, hashCode는 함께 override되어야 한다.

### 동등성 vs 동일성
- 동등성 : `equal`비교, 값(내용)비교
- 동일성 : `==` 비교, 참조비교

### Checked Exception, UnChecked Exception
- Error와 Exception의 차이
    - Error는 주로 JVM에서 발생하는 심각한 문제로, OutOfMemoryError, StackOverflowError 등 시스템 레벨에서 발생하는 오류. 이는 일반적으로 프로그램에서 처리하지 않으며, 회복이 어려운 오류에 속하며, 애플리케이션 코드에서 복구할 수 없는 심각한 문제를 나타낸다.
    - Exception은 프로그램 실행 중 발생할 수 있는 오류 상황. 대부분의 경우 회복 가능성이 있으며, 프로그램 내에서 예외 처리를 통해 오류 상황을 제어할 수 있다.
      - `Checked Exception` : 컴파일 타임에 검출되는 Exception (개발자가 반드시 처리해야함)
        - ex :  FileNotFoundException
        - 해당 Exception에 대해서 어떻게 처리해야하는지 개발단계에서 결정하게 됨
      - `UnChecked Exception`: 런타임시에 검출되는 Exception = RuntimeException (프로그램 로직상의 오류등으로 발생, 선택적 처리)

- [일급 컬렉션이 무엇인가요?](https://www.maeil-mail.kr/question/53) 
  - 일급 컬렉션: 하나의 컬렉션을 감싸는 클래스를 만들고, 해당 클래스에서 컬렉션과 관련된 비즈니스 로직을 관리하는 패턴
     ```java
    // 일급 컬렉션
    public class Orders {
    
        private final List<Order> orders;
    
        public Orders(List<Order> orders) {
            validate(orders); // 검증 수행
            ...
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
