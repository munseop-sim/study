# Spring Exception Handler

## @ExceptionHandler

Spring MVC에서 컨트롤러(`@Controller`)나 `@ControllerAdvice` 클래스의 메서드에 발생하는 **예외를 처리**하는 어노테이션.

### 동작 방식
1. 예외 발생 시 `DispatcherServlet`이 적절한 `HandlerExceptionResolver`를 탐색
2. Spring에 기본 등록된 3개의 `HandlerExceptionResolver`가 우선순위에 따라 동작:
   - **`ExceptionHandlerExceptionResolver`**: 가장 먼저 동작. `@ExceptionHandler`가 등록되어 있는지 확인
   - `ResponseStatusExceptionResolver`: 예외의 타입이나 상태 코드에 따라 적절한 HTTP 응답 생성
   - `DefaultHandlerExceptionResolver`: Spring MVC에 내장된 예외 타입들의 기본 에러 핸들링
3. 발생한 예외가 `@ExceptionHandler`에 등록되어 있는지 확인하고 처리
4. 처리할 수 없는 예외라면 다음 리졸버로 넘김

### 핵심 특징
- 예외가 WAS(톰캣)로 전파되지 않고 **Spring 내부에서 직접 처리**됨
- 사용자 친화적인 에러 메시지 제공, 로깅 등 추가 작업 수행 가능
- 특정 예외 타입을 처리할 메서드를 지정하거나, 파라미터로 처리할 예외를 설정 가능

---

## @ControllerAdvice

**모든 컨트롤러에 대해 전역 기능을 제공**하는 어노테이션.

- `@ControllerAdvice` 클래스 내에 `@ExceptionHandler`, `@InitBinder`, `@ModelAttribute`를 등록하면 **한 곳에서 예외 처리·바인딩을 통합 관리** 가능
- 코드 중복 감소, 유지보수성 향상
- 내부에 `@Component`가 포함되어 있어 **컴포넌트 스캔으로 자동 빈 등록**

### `@ControllerAdvice` vs `@RestControllerAdvice`
- `@RestControllerAdvice` = `@ControllerAdvice` + `@ResponseBody`
- `@ExceptionHandler`와 함께 쓸 때 예외 응답을 **JSON 형태로 자동 직렬화**

---

## CORS (Cross-Origin Resource Sharing)

다른 출처(Origin)의 리소스 요청을 허용하기 위한 HTTP 메커니즘.

**동일 출처 정책(SOP)**: 브라우저는 기본적으로 다른 출처의 요청을 차단

**Preflight 요청**: 실제 요청 전 OPTIONS 메서드로 서버에 허용 여부를 먼저 확인
- `Origin`, `Access-Control-Request-Method`, `Access-Control-Request-Headers` 헤더 포함
- 서버가 `Access-Control-Allow-*` 헤더로 응답

**Spring에서 CORS 설정**:
```java
@CrossOrigin(origins = "http://example.com") // 컨트롤러 레벨
// 또는 WebMvcConfigurer.addCorsMappings()로 전역 설정
```
