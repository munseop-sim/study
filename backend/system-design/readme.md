# System Design

대규모 시스템 설계 및 분산 시스템 패턴을 정리한 디렉토리입니다.

## 📚 Contents

### [consistent_hashing.md](./consistent_hashing.md)
**일관성 해싱 (Consistent Hashing)**
- 분산 시스템에서 데이터 분배 전략
- 노드 추가/제거 시 데이터 재분배 최소화
- Virtual Node를 통한 부하 분산

### [key-value store.md](./key-value%20store.md)
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