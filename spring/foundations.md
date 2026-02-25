# Spring Foundations

## 스프링 사용 이유
1. 편리한 의존성 관리
2. 광범위한 생태계
3. AOP 기반 관심사 분리
4. 유연하고 확장 가능한 구조
5. 체계적인 문서/커뮤니티

## DI (Dependency Injection)
- 스프링의 의존성 주입 방식
- `@Autowired`, 생성자 주입, 메서드 주입 중 생성자 주입 권장
- 생성 시점 주입으로 불변성/순환 참조 예방에 유리

## 스프링에서 자주 쓰는 패턴
- Singleton: 기본 Bean 스코프
- Factory: `BeanFactory`, `ApplicationContext`
- Prototype: 요청마다 새 Bean 생성
- Proxy: AOP
- Template Method: 템플릿 계열 API
- Observer: 이벤트 리스너

## 주요 스테레오타입 애너테이션
- `@Component`: 일반 Bean
- `@Controller` / `@RestController`: 웹 계층 Bean
- `@Service`: 비즈니스 로직 계층
- `@Repository`: 데이터 접근 계층

## Spring vs Spring Boot
- Spring Boot는 자동 설정과 스타터 의존성을 제공
- 내장 서버(Tomcat 등) 기반 실행에 유리
- 관련 문서: [spring_auto_configuration.md](./spring_auto_configuration.md)
