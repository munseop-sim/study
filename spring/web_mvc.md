# Spring Web MVC

## `@ResponseBody` vs `ResponseEntity<T>`
- `@ResponseBody`: 간결하지만 상태코드/헤더 제어가 제한적
- `ResponseEntity<T>`: 상태코드/헤더 제어에 유리

## Filter vs Interceptor
- Filter: 서블릿 컨테이너 레벨 전/후처리
- Interceptor: Spring MVC 핸들러 실행 전/후 처리

## `@ControllerAdvice`
- 전역 예외 처리/바인딩/모델 설정을 중앙화
- `@RestControllerAdvice`는 JSON 응답 중심

## Spring MVC 실행 흐름
1. 요청이 서블릿 컨테이너에 도착
2. `DispatcherServlet` 진입
3. `HandlerMapping` / `HandlerAdapter`로 처리기 결정
4. 인터셉터 `preHandle`
5. 컨트롤러 처리
6. 인터셉터 `postHandle`
7. ViewResolver 또는 HttpMessageConverter
8. 인터셉터 `afterCompletion`

## `@Controller` vs `@RestController`
- `@Controller`: 뷰 반환 중심
- `@RestController`: API(JSON/XML) 응답 중심

## `@RequestBody` vs `@ModelAttribute`
- `@RequestBody`: 본문(JSON 등) 바인딩
- `@ModelAttribute`: 쿼리/폼 데이터 바인딩

## 톰캣(Tomcat)
- 일반적인 WAS 구현체
- 서블릿 생명주기(`init` → `service` → `destroy`) 기반 처리
