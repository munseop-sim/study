# Spring Foundations

## 스프링 사용 이유
1. 편리한 의존성 관리
2. 광범위한 생태계
3. AOP 기반 관심사 분리
4. 유연하고 확장 가능한 구조
5. 체계적인 문서/커뮤니티

---

## Spring Bean으로 관리하는 이유

### 1. 의존성 관리 자동화
- Spring 컨테이너(`BeanFactory`, `ApplicationContext`)가 자동으로 의존성 주입
- **빌드 시점에 순환 의존성 감지** → 설계 오류 조기 발견

### 2. 싱글톤 패턴 구현
- 기본적으로 빈을 **싱글톤으로 관리** → 메모리 사용 최적화, 불필요한 객체 생성 방지
- 여러 서비스에서 동일한 레포지토리를 주입받더라도 동일한 인스턴스 사용

### 3. 생명주기 관리
- `@PostConstruct` (초기화), `@PreDestroy` (소멸) 등으로 빈의 생명주기를 체계적으로 관리

### 4. AOP 지원
- 빈으로 관리되어야만 `@Transactional`, 로깅, 보안 등 공통 관심사를 AOP로 적용 가능

### 5. 테스트 용이성
- `@MockBean`으로 쉽게 Mock 객체로 대체 가능. 단위/통합 테스트가 용이

### 6. 설정의 중앙화
- `@Configuration` + `@Bean`으로 애플리케이션 구성 요소를 한 곳에서 일관되게 관리

---

## 의존성 주입(Dependency Injection)

A 객체가 B 객체를 필요로 할 때, **A 내부에서 B를 직접 생성하지 않고 외부(C)가 생성한 B를 A에게 전달**하는 방식.

### 주입 방식 3가지

| 방식 | 특징 | 사용 시점 |
|---|---|---|
| **생성자 주입** | 객체 생성 시 완전한 상태 보장, 불변성 유지 가능 | 일반적인 고정 의존성 (가장 권장) |
| **Setter 주입** | 선택적 의존성, 객체가 일시적으로 불완전한 상태일 수 있음 | 선택적 의존성 |
| **메서드 주입** | 실행마다 의존 대상이 달라지는 경우 | 일시적·가변적 의존성 |

### 의존성 주입의 이점
- 결합도 감소 → 구현체를 쉽게 교체 가능
- 테스트 시 Mock 객체 주입 용이
- 코드 변경 없이 다양한 실행 구조 구성 가능

참고: [tecoble - 의존관계 주입(DI) 쉽게 이해하기](https://tecoble.techcourse.co.kr/post/2021-04-27-dependency-injection/)

---

## @Component / @Controller / @Service / @Repository 차이

모두 해당 클래스를 **Spring Bean으로 등록**하는 어노테이션. `@ComponentScan`을 통해 자동 등록.

| 어노테이션 | 계층 | 역할 |
|---|---|---|
| `@Component` | 범용 | 특정 계층에 속하지 않는 일반 컴포넌트 (유틸리티 등) |
| `@Service` | 서비스 레이어 | 비즈니스 로직 처리 |
| `@Controller` | 프레젠테이션 레이어 | 웹 요청 처리 (뷰 반환) |
| `@Repository` | 데이터 액세스 레이어 | DB 상호작용 |

### `@Component`로 대체하면 안 되는 이유

- **`@Controller` 대체 불가 (Spring 6 이후)**: `@Controller` 어노테이션이 있는 클래스만 핸들러로 등록됨
- **`@Repository` 대체 시 주의**: `PersistenceExceptionTranslationPostProcessor`에 의한 예외 변환(`DataAccessException`) 미동작
- AOP 포인트컷 정의 시 계층 구분에 활용 가능

참고: [Spring Bean Annotations - Baeldung](https://www.baeldung.com/spring-bean-annotations#repository)

---

## 스프링에서 자주 쓰는 패턴
- Singleton: 기본 Bean 스코프
- Factory: `BeanFactory`, `ApplicationContext`
- Prototype: 요청마다 새 Bean 생성
- Proxy: AOP
- Template Method: 템플릿 계열 API
- Observer: 이벤트 리스너

---

## Spring vs Spring Boot
- Spring Boot는 자동 설정과 스타터 의존성을 제공
- 내장 서버(Tomcat 등) 기반 실행에 유리
- 관련 문서: [configuration.md](./configuration.md)
