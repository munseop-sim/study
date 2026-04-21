# 시스템 설계 패턴

> **관련 문서**
> - [SAGA + Outbox 실전 구현](../msa/saga_outbox_patterns.md) — Outbox 패턴 상세 구현, Polling Publisher, CDC/Debezium, 멱등성 소비자, 면접 Q&A
> - [MSA 분산 트랜잭션 개요](../msa/transaction.md) — 분산 트랜잭션 개념, 2PC, SAGA 배경

---

## 트랜잭셔널 아웃박스 패턴 (Transactional Outbox Pattern)

> 출처: https://www.maeil-mail.kr/question/171

### 개념

**트랜잭셔널 아웃박스 패턴(Transactional Outbox Pattern)** 은 분산 시스템에서 **데이터베이스 쓰기 작업과 메시지/이벤트 발행을 원자적으로 처리**하기 위해 사용하는 패턴입니다.

### 문제: 이중 쓰기(Dual Write)

DB 쓰기와 이벤트 발행을 동시에 하는 경우 원자성을 보장할 수 없습니다.

```java
@Transactional
public void propagateSample() {
   Product product = new Product("신규 상품");
   productRepository.save(product);
   // 트랜잭션 커밋 성공 → 이벤트 발행 실패 가능
   // 이벤트 발행 성공 → 트랜잭션 롤백 가능
   eventPublisher.propagate(new NewProductEvent(product.getId()));
}
```

위 코드에서 발생 가능한 문제:
1. 트랜잭션은 커밋됐지만 이벤트 발행 실패 → **이벤트 유실**
2. 이벤트 발행은 성공했지만 트랜잭션 롤백 → **유령 이벤트 발행**

이러한 문제는 전체 서비스의 **데이터 정합성 문제** 또는 **서비스 장애**로 이어질 수 있습니다.

### 해결: 트랜잭셔널 메시징(Transactional Messaging)

서비스 로직의 실행과 이벤트 발행을 **원자적으로** 함께 수행하는 것을 트랜잭셔널 메시징이라고 합니다.

### 구현 방법

**1단계: Outbox 테이블 생성**

이벤트를 저장하기 위한 별도의 `outbox` 테이블을 만듭니다.

```sql
CREATE TABLE product_outbox (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL,
    event_type VARCHAR(50),
    payload JSONB,
    created_at TIMESTAMP,
    published_at TIMESTAMP  -- NULL: 미발행, NOT NULL: 발행 완료
);
```

**2단계: 같은 트랜잭션에서 이벤트 저장**

```java
@Transactional
public void propagateSample() {
   Product product = new Product("신규 상품");
   productRepository.save(product);
   // 이벤트를 외부 시스템에 직접 발행하는 대신, Outbox 테이블에 저장
   productOutboxRepository.save(new ProductEvent(product.getId()));
   // product와 event는 같은 트랜잭션 → 모두 저장 or 모두 실패 (원자성 보장)
}
```

**3단계: 별도 프로세스가 Outbox 폴링 후 이벤트 발행**

```
[별도 스케줄러/CDC]
    ↓ Outbox 테이블 폴링 (published_at IS NULL)
    ↓ 외부 시스템(Kafka, RabbitMQ 등)에 이벤트 발행
    ↓ published_at 업데이트 (발행 완료 표시)
```

### 핵심 원리

- DB 트랜잭션으로 **원자성** 보장: Product와 이벤트가 모두 저장되거나, 모두 실패
- 별도 프로세스가 **최소 1회(at-least-once)** 이벤트 발행 보장
- 이벤트 수신 측에서 **멱등성(idempotency)** 처리 필요

### Outbox 폴링 구현 방식

1. **폴링(Polling)**: 스케줄러가 주기적으로 Outbox 테이블을 조회
2. **CDC(Change Data Capture)**: Debezium 등을 사용하여 DB 변경 로그를 실시간으로 감지 (Kafka Connector 활용)

### 참고 자료

- [AWS 클라우드 설계 패턴 - 트랜잭션 아웃박스 패턴](https://docs.aws.amazon.com/ko_kr/prescriptive-guidance/latest/cloud-design-patterns/transactional-outbox.html)
- [강남언니 공식 블로그 - 분산 시스템에서 메시지 안전하게 다루기](https://blog.gangnamunni.com/post/transactional-outbox)
- [트랜잭셔널 아웃박스 패턴의 실제 구현 사례 (29CM)](https://medium.com/@greg.shiny82/%ED%8A%B8%EB%9E%9C%EC%9E%AD%EC%85%94%EB%84%90-%EC%95%84%EC%9B%83%EB%B0%95%EC%8A%A4-%ED%8C%A8%ED%84%B4%EC%9D%98-%EC%8B%A4%EC%A0%9C-%EA%B5%AC%ED%98%84-%EC%82%AC%EB%A1%80-29cm-0f822fc23edb)

### 소비자 측 멱등성 처리

at-least-once 전달 보장으로 인해 같은 이벤트가 2번 이상 도착할 수 있으므로, 이벤트 소비 시 멱등성 보장이 필요하다.

> 멱등성 소비자 구현 상세(`processed_events` 테이블, UPSERT, 상태 전이)는 [SAGA + Outbox 실전 구현](../msa/saga_outbox_patterns.md) 참고.

### Polling vs CDC 비교

> Polling Publisher vs CDC(Debezium) 상세 비교와 설정 예시는 [SAGA + Outbox 실전 구현](../msa/saga_outbox_patterns.md) 참고.

### Event Sourcing과의 비교
| 구분 | Outbox 패턴 | Event Sourcing |
|---|---|---|
| 상태 저장 | 현재 상태 저장 (기존 방식) | 이벤트 히스토리만 저장 |
| 이벤트 역할 | 외부 알림용 (부산물) | 시스템의 진실의 원천(Source of Truth) |
| 복잡도 | 낮음~중간 | 높음 (이벤트 리플레이, 스냅샷) |
| 기존 시스템 적용 | 쉬움 (outbox 테이블만 추가) | 어려움 (전체 아키텍처 변경) |
| 적합 상황 | DB 쓰기 + 이벤트 발행 원자성 필요 시 | 완전한 감사 로그, 시간 여행 필요 시 |

### 타임아웃/재시도 전략

발행 실패 시 재시도, Dead Letter 처리, 백프레셔, TTL 관리 등의 전략이 필요하다.

> 재시도 전략 상세(retry_count, Dead Letter, 백프레셔, TTL)는 [SAGA + Outbox 실전 구현](../msa/saga_outbox_patterns.md) 참고.

---

## 이벤트 소싱 (Event Sourcing)

> 출처: https://www.maeil-mail.kr/question/260

### 개념

**이벤트 소싱(Event Sourcing)** 은 데이터의 최종 상태를 저장하는 대신, **상태를 변경시킨 이벤트들의 이력을 저장**하는 방식입니다.

### 일반적인 상태 저장 방식 vs 이벤트 소싱

체스 프로그램 예시:

| 방식 | 저장 내용 | 예시 |
|---|---|---|
| 일반 방식 | 현재 상태 스냅샷 | `{1a: bp, 2b: wp, ...}` (현재 체스판 상태) |
| 이벤트 소싱 | 이벤트 이력(기보) | `검은색 폰 1a로 이동`, `흰색 폰 2b로 이동`, ... |

이벤트 소싱에서는 순서대로 쌓여있는 이벤트를 **재생(replay)** 하여 현재 상태를 도출합니다.

### 장점

1. **완전한 감사 로그(Audit Log)**: 모든 이벤트를 저장하기 때문에 언제든지 특정 시점의 상태 재현 가능
2. **디버깅 용이**: 특정 문제 상황을 이벤트 재생으로 재현 가능
3. **비즈니스 로직 변경 유연성**: 시간에 따라 비즈니스 로직이 달라지는 경우 새로운 규칙에 따라 이벤트를 재생 가능
4. **시간 여행(Time Travel)**: 과거 특정 시점의 상태 조회 가능

### 단점

1. **읽기 성능 저하**: 모든 이벤트를 재생해야 하므로 현재 상태 조회 시 성능 저하
   - 해결책: **스냅샷(Snapshot)** — 특정 시점의 중간 상태를 별도로 저장
2. **대용량 데이터**: 이벤트가 계속 추가되기만 하여 상대적으로 대용량 데이터 처리 필요
3. **이벤트 스키마 관리**: 이벤트 구조 변경 시 하위 호환성 유지 필요

### CQRS와의 관계

이벤트 소싱은 **CQRS(Command Query Responsibility Segregation)** 패턴과 함께 자주 사용됩니다.

```
명령(Command) 측: 이벤트를 이벤트 저장소(Event Store)에 저장
                    ↓
쿼리(Query) 측:  이벤트를 구독하여 읽기 최적화된 뷰(Read Model) 생성
                → 빠른 읽기 성능 제공
```

- **이벤트 소싱** → 쓰기 모델에서 이벤트로 상태 변경 기록
- **CQRS** → 읽기/쓰기 모델 분리로 각각 최적화

### 이벤트 소싱 구현 흐름

```
1. 명령 수신 (예: "폰을 1a로 이동")
2. 비즈니스 규칙 검증
3. 이벤트 생성 (PawnMovedEvent{from: "1b", to: "1a"})
4. 이벤트 저장소에 저장 (append-only)
5. 이벤트 발행 (구독자에게 전달)
6. 읽기 모델(프로젝션) 업데이트
```

### 참고 자료

- [이벤트 소싱(Event Sourcing) 개념](https://mjspring.medium.com/%EC%9D%B4%EB%B2%A4%ED%8A%B8-%EC%86%8C%EC%8B%B1-event-sourcing-%EA%B0%9C%EB%85%90-50029f50f78c)
- [Event Sourcing 맛보기 - 이론편](https://sabarada.tistory.com/231)
