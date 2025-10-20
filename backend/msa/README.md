# MSA (Microservices Architecture)

마이크로서비스 아키텍처에 대한 내용을 정리한 디렉토리입니다.

## 📚 Contents

### [transaction.md](./transaction.md)

#### MSA 개요
- 도메인 분리를 통한 시스템 분할
- 독립적인 개발, 배포, 운영
- 각 시스템별 기술 스택 자유도

#### 트랜잭션 관리
**ACID 속성**
- Atomicity (원자성)
- Consistency (일관성)
- Isolation (격리성)
- Durability (지속성)

**분산 트랜잭션 패턴**

1. **2PC (Two-Phase Commit)**
   - Prepare Phase → Commit Phase
   - Coordinator의 역할과 문제점
   - 장애 시 복구 전략

2. **보상 트랜잭션 (Compensating Transaction)**
   - Commit된 데이터의 롤백 메커니즘
   - 각 서비스별 보상 API 구현

3. **SAGA 패턴**
   - **코레오그래피 (Choreography)**: 이벤트 기반 조율
   - **오케스트레이션 (Orchestration)**: 중앙 조율자 방식
   - 각 패턴의 장단점 및 선택 기준

#### EDA (Event-Driven Architecture)
- 이벤트 소싱을 통한 데이터 관리
- 메시지 브로커 활용
- 장애 복구 전략

#### 구현 프레임워크
- **Eventuate**: Local, Tram
- **Axon Framework**: Event Sourcing, CQRS, DDD 구현

### [api_gateway.md](./api_gateway.md)

#### API Gateway
- 단일 진입점 패턴
- 공통 로직 처리 (인증/인가, 로깅, API 변환)
- Spring Cloud Gateway

#### BFF (Backend For Frontend)
- 클라이언트별 최적화된 API
- GraphQL 활용

## 관련 문서
- [/backend/messaging](../messaging) - Kafka를 활용한 이벤트 기반 아키텍처
- [/backend/network/Proxy.md](../network/Proxy.md) - Reverse Proxy와 Load Balancing
- [/db/db_transaction.md](../../db/db_transaction.md) - 트랜잭션 기초
