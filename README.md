# 백엔드 개발 학습 저장소

백엔드 개발자로서 학습한 내용들을 체계적으로 정리한 저장소입니다.

## 📁 디렉토리 구조

### 🎯 [backend](./backend)
백엔드 개발의 핵심 개념과 기술
- **[architecture](./backend/architecture)**: 소프트웨어 아키텍처 패턴 (Layered, Hexagonal, SOLID)
- **[concurrency](./backend/concurrency)**: 동시성 제어 (Lock, 트랜잭션 격리 수준)
- **[design-patterns](./backend/design-patterns)**: GoF 디자인 패턴
- **[network](./backend/network)**: 네트워크/HTTP/Web (Proxy, 타임아웃, 멱등성, CORS)
- **[messaging](./backend/messaging)**: Kafka 및 메시지 브로커
- **[msa](./backend/msa)**: 마이크로서비스, 분산 트랜잭션, SAGA 패턴
- **[system-design](./backend/system-design)**: 대규모 시스템 설계 (Consistent Hashing, Rate Limiter)
- **[finance](./backend/finance)**: 금융 도메인 기술 (금액 정밀도, 멱등성 키, 이중부기)
- **[tools](./backend/tools)**: 개발 생산성 도구 (Git, IntelliJ)

### 🍃 [spring](./spring)
Spring Framework 및 Spring Boot
- Core: DI, Bean, 설정 바인딩, Auto Configuration
- Data: Transaction, JPA, DB 연결
- Web MVC: Controller, Interceptor, Exception Handler
- Batch / Security: Spring Batch (Chunk/Tasklet), Spring Security + JWT

### 💾 [db](./db)
데이터베이스
- Transaction / Isolation / Lock
- MySQL / Redis
- SQL 연산 특성 (drop, truncate, delete)

### 💻 [cs](./cs)
컴퓨터 과학 기초
- **[data-structure](./cs/data-structure)**: 자료구조 (HashTable, Tree)
- **[operating-system](./cs/operating-system)**: 스레드/프로세스/코어

### ☕ [java](./java)
Java 언어 기초
- JVM Runtime
- Language Basics
- Concurrency
- Exceptions

### 🎯 [kotlin](./kotlin)
Kotlin 언어
- Coroutines (CoroutineScope, Dispatcher, Flow, 구조적 동시성)

### 📚 [book](./book)
독서 노트
- **[README](./book/README.md)**: 도서 노트 인덱스
- **[code_complete](./book/code_complete)**: 코드 컴플리트
- **[objects](./book/objects)**: 오브젝트
- **[java_spring_pragmatism_dev](./book/java_spring_pragmatism_dev)**: 자바 스프링 실용주의 개발

---

## Git Commit 규칙
| Type    | Description                           |
|---------|---------------------------------------|
| feat    | 새로운 기능에 대한 커밋               |
| fix     | 버그수정                              |
| build   | 빌드관련 파일 수정/모듈 설치 또는 삭제 |
| chore   | 자잘한 수정에 대한 커밋               |
| ci      | ci 관련설정 수정에 대한 커밋         |
| docs    | 문서 수정에 대한 커밋                |
| style   | 코드 스타일 변경에 따른 커밋         |
| refactor| 코드 리팩토링에 대한 커밋            |
| test    | 테스트 코드 수정에 대한 커밋         |
| perf    | 성능 개선에 대한 커밋                |
