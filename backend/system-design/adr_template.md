# ADR 템플릿

ADR(Architecture Decision Record)은 중요한 설계 결정을 짧고 명확하게 기록하기 위한 문서입니다.

---

## 사용 규칙

- 번호를 순차적으로 부여 (`ADR-001`, `ADR-002`)
- 상태를 반드시 기록 (`Proposed`, `Accepted`, `Deprecated`, `Superseded`)
- 결론뿐 아니라 대안/트레이드오프를 함께 기록
- 바뀐 결정은 기존 ADR을 수정하지 말고 새 ADR로 대체 관계를 남김

---

## 템플릿

```markdown
# ADR-XXX: <결정 제목>

- 상태: Proposed | Accepted | Deprecated | Superseded
- 작성일: YYYY-MM-DD
- 작성자: <name>
- 관련 이슈/문서: <link>

## 배경 (Context)
문제 상황, 제약 조건, 목표 지표를 서술한다.

## 결정 (Decision)
무엇을 선택했는지 한 문단으로 명확히 적는다.

## 대안 (Alternatives)
1. 대안 A: 장점/단점
2. 대안 B: 장점/단점

## 트레이드오프 (Trade-offs)
성능, 복잡도, 운영비, 확장성, 팀 역량 관점에서 비교한다.

## 결과 (Consequences)
### 긍정적 결과
- 기대 효과

### 부정적 결과
- 수용한 비용/리스크

## 실행 계획 (Implementation Plan)
- 단계 1
- 단계 2

## 검증 계획 (Validation)
- 어떤 메트릭/테스트로 유효성 검증할지 명시

## 롤백/대체 전략 (Rollback)
- 실패 시 복구 방안
```

---

## 작성 예시 (축약)

```markdown
# ADR-007: 주문 생성 경로를 동기 DB 트랜잭션 + Outbox 패턴으로 구성

- 상태: Accepted
- 작성일: 2026-03-04

## 배경
주문 생성 후 결제/재고 이벤트 전파가 필요하며, 메시지 유실은 허용되지 않는다.

## 결정
주문 DB 커밋과 이벤트 기록을 동일 트랜잭션으로 처리하고, Outbox Relay가 Kafka로 발행한다.

## 대안
1. API에서 즉시 Kafka 발행: 단순하지만 DB 커밋/발행 원자성 문제
2. 2PC: 일관성은 강하지만 운영 복잡도와 성능 비용 높음

## 트레이드오프
Outbox는 구현 복잡도가 증가하지만 유실 방지와 재처리 용이성이 높다.
```
