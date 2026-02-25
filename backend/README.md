# Backend 개발 학습 자료

백엔드 개발에 필요한 핵심 개념과 기술들을 정리한 디렉토리입니다.

## 📁 디렉토리 구조

### 🏗️ [architecture](./architecture)
소프트웨어 아키텍처 패턴 및 설계 원칙
- Layered Architecture, Hexagonal Architecture
- SOLID 원칙

### 🔄 [concurrency](./concurrency)
동시성 제어 및 병렬 처리
- RDB Lock (낙관적 락, 비관적 락)
- Redis 분산 락
- 트랜잭션 격리 수준

### 🎨 [design-patterns](./design-patterns)
디자인 패턴 (GoF 패턴)
- 생성 패턴: Builder, Factory, Singleton
- 구조 패턴: Decorator, Proxy, Adapter, Facade
- 행동 패턴: Observer, Strategy, Template

### 🌐 [network](./network)
네트워크 및 HTTP/Web 통신
- HTTP Header, TCP Handshake
- Proxy (Forward/Reverse)
- 네트워크 타임아웃
- 웹 기반 백엔드 기초 (멱등성, CORS, SSR/CSR, WAS vs 웹서버)

### 📮 [messaging](./messaging)
메시지 브로커 및 이벤트 기반 아키텍처
- Kafka 설치 및 운영
- Kafka 심화

### 🏢 [msa](./msa)
마이크로서비스 아키텍처
- API Gateway, BFF 패턴
- 분산 트랜잭션 (2PC, SAGA, 보상 트랜잭션)
- EDA (Event-Driven Architecture)

### ⚙️ [system-design](./system-design)
대규모 시스템 설계
- Consistent Hashing
- Key-Value Store
- Rate Limiter
- Dynamic Configuration

### 🛠️ [tools](./tools)
개발 생산성 도구
- Git
- IntelliJ 단축키 (Mac/Windows)

---

## 관련 디렉토리
- [/spring](../spring) - Spring Framework 관련 내용
- [/db](../db) - 데이터베이스 관련 내용
- [/cs](../cs) - 컴퓨터 과학 기초
- [/book](../book) - 독서 노트
