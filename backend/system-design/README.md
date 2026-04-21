# System Design

대규모 시스템 설계 및 분산 시스템 패턴을 정리한 디렉토리입니다.

## 📚 Contents

### [design_process.md](./design_process.md)
**설계 프로세스**
- 요구사항(FR/NFR) 정리
- 트래픽/용량 추정
- 아키텍처/데이터/API/운영 설계 순서
- 최소 산출물 및 실패 패턴

### [adr_template.md](./adr_template.md)
**ADR 템플릿**
- 아키텍처 의사결정 기록 표준
- 대안/트레이드오프/검증/롤백까지 포함
- 실전 작성 예시 제공

### [case_study_order_system.md](./case_study_order_system.md)
**주문 시스템 설계 케이스 스터디**
- 요구사항부터 용량 추정, 아키텍처, API, 장애 대응까지 연결
- Outbox, 멱등성 키, 관측성 지표 예시

### [design_review_checklist.md](./design_review_checklist.md)
**설계 리뷰 체크리스트**
- 요구사항, 정합성, 확장성, 복구, 보안, 운영 관점 점검
- 리뷰 결과 기록 템플릿 포함

### [consistent_hashing.md](./consistent_hashing.md)
**일관성 해싱 (Consistent Hashing)**
- 분산 시스템에서 데이터 분배 전략
- 노드 추가/제거 시 데이터 재분배 최소화
- Virtual Node를 통한 부하 분산

### [key_value_store.md](./key_value_store.md)
**Key-Value 저장소 설계**
- CAP 이론 (Consistency, Availability, Partition Tolerance)
- 데이터 복제 및 동기화
- Eventual Consistency
- 대표 사례: Redis, DynamoDB, Cassandra

### [rate_limiter.md](./rate_limiter.md)
**Rate Limiter (속도 제한기)**
- API 요청 제한 패턴
- 알고리즘
  - Token Bucket
  - Leaky Bucket
  - Fixed Window
  - Sliding Window
- 분산 환경에서의 Rate Limiting (Redis 활용)

### [dynamic_configuration.md](./dynamic_configuration.md)
**동적 설정 관리**
- 애플리케이션 재시작 없는 설정 변경
- 설정 버전 관리
- 분산 시스템에서의 설정 동기화
- 대표 도구: Spring Cloud Config, Consul, etcd

### [circuit_breaker.md](./circuit_breaker.md)
**Circuit Breaker 패턴과 Resilience4j 구현**
- Circuit Breaker 상태 머신 (Closed / Open / Half-Open)
- Resilience4j 설정 및 Spring Boot 통합
- Fallback 전략 및 메트릭 모니터링

### [observability_stack.md](./observability_stack.md)
**관찰성 3 Pillars (Logs / Metrics / Traces) 통합 스택**
- Logs: 구조적 로깅, MDC, ELK/Loki 연동
- Metrics: Micrometer + Prometheus + Grafana
- Traces: OpenTelemetry, Zipkin/Jaeger 분산 추적

### [ai_assisted_backend_development.md](./ai_assisted_backend_development.md)
**AI 보조 개발 시대의 시니어 백엔드 역할과 검증 체크리스트**
- AI 생성 코드의 신뢰성 평가 기준
- 아키텍처 검증 포인트 (정합성, 보안, 성능)
- 시니어 엔지니어의 판단 영역과 AI 위임 경계

## 설계 시 고려사항

### 확장성 (Scalability)
- 수평적 확장 vs 수직적 확장
- Stateless 설계
- 샤딩 전략

### 가용성 (Availability)
- 장애 격리
- 헬스 체크
- Graceful Degradation

### 일관성 (Consistency)
- Strong Consistency vs Eventual Consistency
- 분산 트랜잭션

### 성능 (Performance)
- 캐싱 전략
- 비동기 처리
- 데이터베이스 최적화

## 관련 문서
- [/backend/msa](../msa) - 마이크로서비스 아키텍처
- [/backend/network](../network) - 네트워크 및 프록시
- [/db](../../db) - 데이터베이스 설계
