# Exception Handler
## `@ExceptionHandler`란?
- @ExceptionHandler 애너테이션은 Spring MVC에서 컨트롤러(@Controller)나 전역 예외 처리를 위한 @ControllerAdvice 클래스의 메서드에서 발생하는 예외를 처리하는 데 사용
- 특정 예외를 처리하는 메서드를 지정하거나 메서드의 파라미터로 처리할 예외를 설정할 수 있음
- @ExceptionHandler를 통해 개발자가 예외를 직접 처리함으로써, 예외가 WAS에 던져지지 않게 할 수 있음

## 동작방식
- Spring MVC에서 예외가 발생하면 `DispatcherServlet`이 적절한 `HandlerExceptionResolver`를 찾아 예외를 처리
- HandlerExceptionResolver
  - 기본적으로 3가지 리졸버가 존재함
  - `ExceptionHandlerExceptionResolver`: 가장먼저 동작, @ExceptionHandler가 등록되어 있는지 확인
  - `ResponseStatusExceptionResolver`: 예외의 타입이나 예외에서 반환된 상태 코드에 따라 적절한 HTTP 응답을 생성
  - `DefaultHandlerExceptionResolver`: Spring MVC에 내장된 예외 타입들과 관련된 기본적인 에러 핸들링을 제공
