# Spring Web MVC

## Spring MVC 실행 흐름

### View를 응답하는 경우
1. 클라이언트 HTTP 요청 → **DispatcherServlet** (Front Controller 역할)
2. **HandlerMapping**으로 URL에 매핑된 핸들러(컨트롤러) 조회
3. **HandlerAdapter**를 통해 핸들러 실행 준비
4. HandlerAdapter가 실제 컨트롤러 메서드 호출
5. 컨트롤러는 결과 데이터를 **Model**에 담고 **View 이름** 반환
6. **ViewResolver**가 View 이름으로 적절한 뷰 탐색
7. 뷰를 사용해 최종 HTML 응답 생성

### JSON/문자열을 응답하는 경우 (MessageConverter 동작)
1. HTTP 요청 → DispatcherServlet
2. HandlerMapping으로 핸들러 조회
3. `RequestMappingHandlerAdapter`가 **ArgumentResolver**를 호출해 파라미터 값 생성
   (이때 ArgumentResolver가 HttpMessageConverter를 사용해 객체 생성)
4. 컨트롤러가 서비스·DAO 호출해 비즈니스 로직 처리
5. **ReturnValueHandler**가 HttpMessageConverter를 호출해 응답 결과 생성
   (`@ResponseBody`, `HttpEntity` 등 처리)

> HTTP Accept 헤더, 반환 타입, Content-Type 조합으로 적절한 **HttpMessageConverter 자동 선택**

---

## Filter vs Interceptor

### Filter
- **서블릿 컨테이너(Tomcat 등) 수준**에서 동작
- 인터페이스: `jakarta.servlet.Filter`
- 메서드: `doFilter()` → FilterChain으로 다음 필터 또는 서블릿에 전달
- 주요 용도: 요청 로깅, 인증, 인코딩 설정, CORS 처리, 캐싱, 압축
- 순서 설정: `web.xml` 또는 `@WebFilter`

### Interceptor
- **Spring MVC 핸들러 수준**에서 동작 (DispatcherServlet 내부)
- 인터페이스: `HandlerInterceptor`
- 메서드:
  - `preHandle()`: 컨트롤러 호출 전
  - `postHandle()`: 컨트롤러 실행 후, 뷰 렌더링 전
  - `afterCompletion()`: 뷰 렌더링 후
- 주요 용도: 인증/권한 검사, 세션 검사, 성능 모니터링
- 순서 설정: `WebMvcConfigurer.addInterceptors()`

### 핵심 차이 비교

| 항목 | Filter | Interceptor |
|---|---|---|
| 인터페이스 | `jakarta.servlet.Filter` | `HandlerInterceptor` |
| 동작 수준 | 서블릿 컨테이너 | Spring MVC 컨트롤러 |
| Spring Context 접근 | 불가 | 가능 |
| 실행 시점 | 서블릿 이전/이후 (가장 먼저) | 컨트롤러 이전/이후 (Filter 이후) |
| 세밀한 핸들러 제어 | 불가 | 가능 (핸들러 메서드 어노테이션 확인 가능) |

### Filter vs Interceptor vs AOP 3자 비교

| 구분 | Filter | Interceptor | AOP |
|---|---|---|---|
| 관리 주체 | 서블릿 컨테이너 (Tomcat) | Spring MVC | Spring IoC |
| 실행 시점 | DispatcherServlet 이전 | DispatcherServlet 이후, Controller 이전/이후 | 메서드 실행 전/후/주변 |
| 구현 방식 | `javax.servlet.Filter` | `HandlerInterceptor` | `@Aspect` + `@Around`/`@Before`/`@After` |
| Spring Bean 접근 | 제한적 (`DelegatingFilterProxy` 필요) | 가능 | 가능 |
| 적용 대상 | URL 패턴 기반 (모든 요청) | Handler(Controller) 기반 | 메서드 레벨 (서비스/리포지토리 등 포함) |
| 예외 처리 | try-catch 직접 처리 | `@ControllerAdvice` 불가 | `@ControllerAdvice` 불가 |
| 대표 사용 사례 | 인코딩, CORS, 보안(Spring Security), 로깅 | 인증/인가 체크, 로깅, 공통 데이터 세팅 | 트랜잭션, 로깅, 성능 측정, 권한 검사 |

### 실행 순서 다이어그램

```
HTTP 요청 → Filter → DispatcherServlet → Interceptor(preHandle) → Controller
                                        ← Interceptor(postHandle) ← Controller
           ← Filter ← DispatcherServlet ← Interceptor(afterCompletion)

AOP는 별도 레이어: Service/Repository 메서드 호출 시 프록시를 통해 적용
```

### 선택 기준 가이드

- **웹 요청/응답 자체를 변경** (인코딩, 압축) → **Filter**
- **Controller 전후 공통 처리** (인증, 로깅, 데이터 세팅) → **Interceptor**
- **비즈니스 로직 횡단 관심사** (트랜잭션, 캐싱, 성능 측정) → **AOP**

---

## @Controller vs @RestController

| 어노테이션 | 역할 |
|---|---|
| `@Controller` | **뷰(View)를 반환**하는 컨트롤러에 사용. 반환값을 ViewResolver가 해석 |
| `@RestController` | **RESTful API** 구현. `@Controller` + `@ResponseBody` 결합. 반환값이 자동으로 JSON/XML 직렬화 |

- `@Controller`에서 JSON 응답을 원할 경우 메서드에 `@ResponseBody`를 추가해야 함
- `@RestController`는 모든 메서드에 `@ResponseBody`가 자동 적용됨

---

## @ResponseBody / ResponseEntity 차이

### `@ResponseBody` / `ResponseEntity<T>` 있을 때
- 컨트롤러 반환값을 **HTTP 응답 본문(Body)에 직접** 씀
- 자바 객체를 자동으로 JSON/XML 등으로 직렬화

### `@ResponseBody` / `ResponseEntity<T>` 없을 때
- 반환값을 **뷰(View) 이름으로 해석**
- ViewResolver가 해당 뷰를 찾아 HTML 응답 생성

### `@ResponseBody` vs `ResponseEntity<T>` 비교

| 항목 | `@ResponseBody` | `ResponseEntity<T>` |
|---|---|---|
| 코드 간결성 | 간결 | 코드량 증가 |
| 상태코드/헤더 제어 | 어려움 | 유연하게 변경 가능 |
| 사용 목적 | 단순 응답 | HTTP 세부 제어가 필요한 경우 |

---

## @RequestBody vs @ModelAttribute

| 항목 | `@RequestBody` | `@ModelAttribute` |
|---|---|---|
| 데이터 위치 | HTTP Body | Query String / Form Data |
| 변환 방식 | HttpMessageConverter (Jackson) | ModelAttributeMethodProcessor |
| Content-Type | `application/json` | `application/x-www-form-urlencoded`, `multipart/form-data` |

- **`@RequestBody`**: 기본 생성자 + getter/setter 필요. Jackson의 ObjectMapper를 통해 JSON → Java 객체 역직렬화
- **`@ModelAttribute`**: 메서드 레벨에서는 Model에 속성 추가, 파라미터 레벨에서는 폼 데이터 바인딩

참고: [tecoble - @RequestBody vs @ModelAttribute](https://tecoble.techcourse.co.kr/post/2021-05-11-requestbody-modelattribute/)

---

## 톰캣(Tomcat)

- **웹 서버 + 웹 컨테이너(서블릿 컨테이너)**를 결합한 형태의 WAS(Web Application Server)
- 주요 역할: JSP·서블릿 처리, 서블릿 수명 주기 관리, URL-서블릿 매핑, HTTP 요청/응답, 필터 체인 관리

### 서블릿 생명주기
- `init()`: 서블릿 컨테이너가 서블릿 최초 생성 시 호출
- `service()`: 이후 모든 요청마다 호출 → HTTP 메서드에 따라 `doGet()` / `doPost()` 분기
- `destroy()`: 서블릿 종료 요청 시 호출

### 서블릿 동작 흐름
1. 사용자 URL 요청 → 서블릿 컨테이너로 전송
2. `HttpServletRequest`, `HttpServletResponse` 생성
3. 서블릿 매핑 정보로 처리할 서블릿 탐색
4. `service()` 메서드 호출 → HTTP 메서드에 따라 `doGet()` / `doPost()` 호출
5. 응답 처리 후 `HttpServletResponse`로 반환

---

## PRG 패턴 (Post-Redirect-Get)

POST 요청 처리 후 Redirect(302)를 통해 GET 요청으로 전환하는 패턴.

**문제 상황**: 폼 제출(POST) 후 브라우저에서 새로고침하면 동일한 POST 요청이 재전송됨 → 데이터 중복 등록

**해결**: POST 처리 완료 후 `302 Found` + Location 헤더로 GET 페이지로 리다이렉트
- 새로고침해도 GET 요청만 재전송
- 중복 등록 방지
