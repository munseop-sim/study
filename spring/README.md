# Spring

Spring Framework / Spring Boot 학습 내용을 주제별로 분류한 인덱스입니다.

## 📚 Contents

### Core
- [foundations.md](./foundations.md): 스프링 사용 이유, DI, 빈 등록 애너테이션, Spring vs Spring Boot
- [configuration.md](./configuration.md): `@Value`, `@ConfigurationProperties`, 커넥션 풀
- [spring_auto_configuration.md](./spring_auto_configuration.md): 자동 구성 개념

### Data
- [transaction.md](./transaction.md): 스프링 트랜잭션
- [JPA.md](./JPA.md): JPA 기본
- [config/spring-jpa.md](./config/spring-jpa.md): Spring + JPA 설정
- [config/mysql_spring_connection_management.md](./config/mysql_spring_connection_management.md): MySQL 연결 관리

### Web MVC
- [web_mvc.md](./web_mvc.md): MVC 흐름, Controller 계열, Filter/Interceptor
- [exception_handler.md](./exception_handler.md): 예외 처리

### Batch / Security
- [spring_batch.md](./spring_batch.md): Spring Batch 구조 (Chunk/Tasklet) + 트랜잭션 전파 심화
- [spring_security.md](./spring_security.md): Spring Security 아키텍처 + JWT 인증/인가

### Mapping / Utility
- [mapstruct_intro.md](./mapstruct_intro.md): MapStruct
- [async_observability.md](./async_observability.md): 동기/비동기, 로그/메트릭

## 분류 기준
- Spring 프레임워크 내부 동작/설정/웹 계층: `spring`
- 프록시, CORS, SSR/CSR 등 HTTP 일반 개념: `backend/network`
