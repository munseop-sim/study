### 스프링 사용이유
1. 편리한 의존성관리
2. 광범위한 생태계
3. AOP를 통해 비즈니스로직과 부가적인 로직의 분리가 손쉽게 가능
4. 유연하고 확장가능
5. 체계화된 문서 제공

### DI
- Dependency Injection
- 스프링에서 의존성 주입을 말함
- @Autowired, 생성자주입, 메소드주입 등 여러가지 방법이 있으나 생성자 주입을 권장함
- 생성자로 주입하게 되면 리커시브한 참조도 예방하게 되고,객체생성시에 주입을 받게되므로 변경 불가
### [Spring transaction](transaction.md)
### [Spring JPA](config/spring-jpa.md)
### [mySQL, Spring Connection 관리](config/mySQL_Spring_Connection_관리.md)
### 스프링에서 사용되는 패턴
- Singleton : Bean은 스프링 컨터이너에서 기본적으로 하나의 객체만 생성함
- Factory : 객체의 생성로직을 캡슐화하여 클라이언트가 직접 객체를 성성하지 않도록 함 &rarr; BeanFactory, ApplicationContext..
- ProtoType Pattern: 요청할때마다 새로운 객체를 생성하는 패턴 &rarr; bean의 scope를 prototype으로 설정하면 bean의 요청시마다 생성됨
- Proxy 패턴: 객체의 기능을 확정하거나 제어하기 위해 대리 객체를 사용하는 패턴 &rarr; AOP
- Template Method패턴: 알고리즘의 구조를 정의하고 일부단계를 서브클래스에서 오버라이드하여 알고리즘의 일부동작을 변경하는 패턴
- Observer 패턴: 스프링 이벤트 리스너

### `@Value`Annotation
- 단일값 바인딩, RelaxedBiding 적용되지 않음
- **빈으로 등록되지 않으면 등록된 값을 읽어올 수 없음**
- _RelaxedBiding : 프로퍼티 이름이 조금 달라도 유연하게 바인딩을 시켜주는 규칙을 의미_
- `@ConfigurationProperties`:
  - 어노테이션은 프로퍼티에 있는 값을 클래스로 바인딩하기 위해 사용
  - 여러개 값 바인딩 가능
  - RelaxedBiding 적용
  - 


### [@Exception Handler](exception_handler.md)

### `@ResponseBody` vs `ResponseEntity<T>`
- `@ResponseBody`: 코드가 한결 간결해지나, 상태코드와 헤더를 유연하게 변경하기 어려움
- `ResponseEntity<T>`: 코드의 복잡성은 다소 증가하나, 상태코드와 헤더를 유연하게 지정할 수 있음.

### `(Servlet) Filter` vs `(Handler) Interceptor`
- filter
  - Filter는 요청 및 응답의 전처리와 후처리를 수행하고 서블릿 컨테이너에 의해 실행되는 Java 클래스입니다. 주로 요청 로깅, 인증, 인코딩 설정, CORS 처리, 캐싱, 압축 등의 공통 기능을 구현하는 데 사용
  - 특징
    - Filter는 서블릿 컨테이너(예: Tomcat) 수준에서 동작. 
    - 모든 요청이 서블릿으로 전달되기 전에 Filter를 거친다.
    - 생명 주기: Filter는 doFilter 메서드를 통해 요청 및 응답을 처리. FilterChain을 통해 다음 필터 또는 최종 서블릿으로 요청을 전달
    - 순서: web.xml이나 @WebFilter 애노테이션을 통해 설정할 수 있으며, 필터의 순서는 설정 파일에서 정의
- Interceptor
  - Interceptor는 특정 핸들러 메서드 실행 전후에 공통 기능을 구현
  - 주로 요청 로깅, 인증, 권한 검사, 세션 검사, 성능 모니터링 등을 수행하는 데 사용
  - 특징
    - Interceptor는 Spring MVC의 핸들러 수준에서 동작
    - Dispatcher Servlet이 컨트롤러를 호출하기 전에 Interceptor를 거친다.
    - 생명 주기
      - preHandle 메서드: 컨트롤러의 메서드가 호출되기 전에 실행
      - postHandle 메서드: 컨트롤러의 메서드가 실행된 후, 뷰가 렌더링되기 전에 실행.
      - afterCompletion 메서드: 뷰가 렌더링된 후 실행
    - 순서: WebMvcConfigurer를 구현한 클래스에서 addInterceptors 메서드를 사용하여 설정
    - 인터셉터의 순서는 등록 순서에 따른다.

### 로그 vs 메트릭
- 로그는 프로그램의 흐름에 따른 상태값을 출력 또는 저장하기 위한용도
  - `시스템이 무엇을 했는지`
- 메트릭: 시스템의 상태(CPU, 에러발생, 네트워크I/O 등)를 파악하기 위함
  - `시스템의 전반적인 상태`

### 동기 vs 비동기
- 동기 : 제어권이 반환되지 않음.
- 비동기: 제어권을 즉시 반환하며, 블로킹되지 않음. 호출결과는 callback 함수로 처리
  - 스프링에서는 @Async 어노테이션을 사용하여 비동기 처리를 수행
  - @Async 가 적용된 메서드에서 발생하는 예외는 호출자에게 전파되지 않음. 비동기 메서드에서 예외를 정상적으로 처리하기 위해서는 별도의 비동기 예외 처리기를 사용
    1. `AsyncUncaughtExceptionHandler` 구현(제일 많이 사용)
    2. `Future`, `CompletableFuture`를 사용하여 예외 처리 : caller 에서 처리한다.
    3. 메서드 내부에서 자체적으로 처리 -> 메세지 알람들의 처리할때나..사용(비추)
  - @Async 어노테이션은 **프록시 기반**으로 동작하기 때문에 같은 클래스 내부에서 직접 호출하는 경우 별도의 스레드에서 메서드가 실행되지 않는다.

### ControllerAdvice에 대해 설명
- `@ControllerAdvice`는 모든 컨트롤러에 대해 전역 기능을 제공하는 애너테이션. 
- `@ControllerAdvice`가 선언된 클래스에 @ExceptionHandler, @InitBinder, @ModelAttribute를 등록하면 예외 처리, 바인딩 등을 한 곳에서 처리할 수 있어, 코드의 중복을 줄이고 유지보수성을 높일 수 있음.
  - @ExceptionHandler: 전역 Exception Handler
  - @InitBinder: **특정 컨트롤러 또는 전역 데이터 바인딩 설정**을 사용자화할 수 있도록 도와주는 어노테이션
    - 특정 요청 파라미터의 데이터 변환 처리 (커스텀 바인더 사용).
    - 사용자가 원하는 방식으로 데이터 포맷 지정 (e.g., 날짜 포맷 지정).
    - 전역에서 불필요한 요청 파라미터 바인딩 방지.
  - @ModelAttribute: **모든 컨트롤러에서 공통적으로 사용할 모델 데이터**를 설정하거나, 요청 파라미터를 모델 객체에 바인딩해주는 역할
    - 전역적으로 뷰에 공통 데이터를 제공하거나, 공통 객체를 모델에 추가할 때 유용.
    - 비동기 요청이나 페이지 렌더링에서 공통적으로 필요한 데이터를 다룰 때 많이 사용.
- `@ControllerAdvice`는 내부에 `@Component`가 포함되어 있어 컴포넌트 스캔 과정에서 빈으로 등록. 
- `@RestControllerAdvice`는 내부에 `@ResponseBody`를 포함하여 `@ExceptionHandler`와 함께 사용될 때 예외 응답을 Json 형태로 내려준다는 특징

### Spring MVC의 실행흐름
1. 요청(Request)이 서블릿 컨테이너(톰캣, 제티..)에 도착하고 이는 1차적으로 필터를 통과하여 스프링의 DispatcherServlet에 전달됨.
2. DispatcherServlet 에서는 HandlerMapping을 통하여 어떻게 처리해야될지 판단
3. HandlerMapping을 통해서 어떤 Controller에서 처리해야될지 결정이 나면 이는 HandlerAdapter를 통해 해당 처리기에 전달된다.
4. (선택적실행) 인터셉터 실행(preHandle)
5. 해당 처리기(Handler)에서 요청에 대한 처리를 완료한후
6. (선택적실행) 인터셉터 실행(postHandle)
7. View 존재유무
   1. @Controller: ViewResolver를 통해서 View를 결정한다.
   2. @RestController: HttpMessageConverter를 통해 JSON, XML등의 특정 형식의 데이터로 변환된다.
8. (무조건실행) 인터셉터 실행(afterCompletion) : exception에 상관없이 항상 실행
9. 요청에 대한 응답이 DispatcherServlet &rarr; 서블릿 컨테이너를 통해 요청자에게 응답결과가 전달된다.

### @Controller 와 @RestController
- @Controller: 주로 뷰를 반환하는 컨트롤러를 정의할 때 사용
  - 메서드가 반환하는 값은 뷰리졸버에 의해 해석되어 JSP, Thymeleaf등과 같은 템플릿 엔진을 통해 HTML을 생성한다. 
- @RestController: RESTful 웹서비스 API를 정의할 때 사용
  - 반환값은 주로 JSON, XML형태로 HTTP응답 본문에 포함된다. (=`@Controller + @ResponseBody`)


### RequestBody VS ModelAttribute의 차이점
- @RequestBody: 요청에 있는 본문에 있는 값을 바인딩할 때 사용
  - HttpMessageConverter를 거쳐서 JSON값을 객체로 변환(역직렬화)
  - `record`타입의 경우 기본생성자를 정의하지 않아도 모든 필드를 초기화하는 생성자를 제공
- @ModelAttribute: `요청파라미터`나 `multipart/form-data`형식을 바인딩할 때 사용 
- - [RequestBody VS ModelAttribute의 차이점](https://www.maeil-mail.kr/question/14)

### Tomcat
- 웹서버 + 웹컨테이너의 결합
- 현재 가장 일반적이고 많이 사용되는 WAS
- 서블릿?
  - 자바를 이용해 웹서비스를 만들기 위한 스펙. 클라이언트가 프로그램으로 요청을 보내면 그 요청에 대한 결과를 응답해주기 위해 사용.
  - 생명주기: 사용자의 요청이 들어오면 서블릿 컨테이너가 서블릿이 존재하는지 확인하고 없는 경우 init()메서드를 호출하여 생성. 이후 요청은 service()메서드를 실행. 만약에 서블릿에 종료요청이 오는 경우에 `destroy()`메서드 호출
- [톰캣에 대해서 설명 LINK](https://www.maeil-mail.kr/question/22)

### Spring, SpringBoot 차이점
- SpringBoot
  - 관련 의존성을 Auto Configuration을 통해 관리할 수 있다.
  - starter의존성 통합 제공
  - 내장서버 존재(Tomcat, Jetty..) jar파일 배포후에 바로 실행(war파일 톰캣배포 필요없음)
  - `@EnableAutoconguration`, `@SpringBootApplication`을 통해 자동설정을 활성화환다.
### [spring boot auto-configuration](spring_auto_configuration.md)

### @Component, @Controller, @Service, @Repository의 차이에 대해서 설명
- @Component
  - 일반적인 bean으로 등록하기 위해 사용
- @Controller
  - 일반적으로 뷰를 리턴하는 컨트롤러 빈에 대해서 붙임
- @RestController
  - JSON, XML등을 반환해야하는 REST API 컨트롤러 빈
- @Service
  - 비지니스 로직 작동되는 빈
  - 트랜잭션 선언
- @Repository
  - 데이터베이스와 상호작용하는 빈
  - Spring6이후 버전에 대해서 @Repository를 붙이지 않고 @Component로 하게 되면  PersistenceExceptionTranslationPostProcessor에 의해 예외가 DataAccessException으로 변환되지 않음. 이 경우 데이터 액세스 계층에서 발생하는 예외 처리에 영향을 미칠 수 있음. 


### 데이터베이스 커넥션 풀(Connection Pool)을 사용하지 않으면 어떤 문제가 발생할 수 있나요?
- 데이터베이스와 상호작용하는 모든요청에 연결-질의-연결종료 행위를 하게 되면 시스템의 부하가 증가할 수 있음
- 또한 데이터베이스의 최대 연결수를 초과할 수도 있음. 
- 이를 방지하기위해 설정된 값을 바탕으로 미리 데이터베이스에 연결된 커넥션을 pool로 관
- 커넥션풀의 사이즈
  - 커넥션풀을 사용하는 주체는 스레드이기 때문에 커넥션과 스레드풀을 연결지어 생각해야함.
  - 커넥션풀, 스레드풀 사이즈의 균형이 맞더라도, 너무 큰 사이즈로 설정하면 데이터베이스 서버, 애플리케이션 서버의 메모리와 CPU를 과도하게 사용하게 되므로 성능이 저하됨.
- [데이터베이스 커넥션 풀(Connection Pool)을 사용하지 않으면 어떤 문제가 발생할 수 있나요? LINK](https://www.maeil-mail.kr/question/88)
- 
