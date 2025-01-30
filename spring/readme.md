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
### [spring boot auto-configuration](spring_auto_configuration.md)

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

- [Spring MVC의 실행흐름](https://www.maeil-mail.kr/question/11)
- [@Controller 와 @RestController 의 차이점](https://www.maeil-mail.kr/question/12)
- [ControllerAdvice에 대해 설명](https://www.maeil-mail.kr/question/13)
- [RequestBody VS ModelAttribute의 차이점](https://www.maeil-mail.kr/question/14)
- [톰캣에 대해서 설명](https://www.maeil-mail.kr/question/22)

- [Spring, SpringBoot 차이점](https://www.maeil-mail.kr/question/24)
- [@Component, @Controller, @Service, @Repository의 차이에 대해서 설명](https://www.maeil-mail.kr/question/72)
- [데이터베이스 커넥션 풀(Connection Pool)을 사용하지 않으면 어떤 문제가 발생할 수 있나요?](https://www.maeil-mail.kr/question/88)
- 