# 케이스 스터디: 주문 시스템 설계

설계 흐름을 실제로 연습하기 위한 샘플 시나리오입니다.

---

## 1. 문제 정의

이커머스 주문 시스템을 설계한다.

목표:
- 주문 생성 성공률 99.9% 이상
- 피크 시점 p95 latency 300ms 이하
- 중복 주문 방지

---

## 2. 요구사항

### 기능 요구사항
- 주문 생성
- 주문 조회
- 결제 요청/결제 결과 반영
- 재고 차감
- 주문 취소

### 비기능 요구사항
- 고가용성: 99.9%
- 주문/결제 데이터 정합성 보장
- 장애 시 재처리 가능
- 관측성 확보 (지연, 실패율, 재시도 횟수)

---

## 3. 가정 및 용량 추정

- DAU: 200,000
- 피크 주문 QPS: 500
- 평균 주문 payload: 2KB
- 읽기:쓰기 = 8:1

대략적 저장량:
- 주문 1건 3KB, 일 300만 건이면 일 9GB 증가

---

## 4. 아키텍처 초안

구성:
- API Gateway
- Order Service
- Payment Service
- Inventory Service
- PostgreSQL (주문 원장)
- Redis (조회 캐시, 멱등 키)
- Kafka (도메인 이벤트)
- Worker (비동기 후처리)

흐름(주문 생성):
1. 클라이언트가 Idempotency-Key와 함께 주문 요청
2. Order Service가 중복 요청 확인
3. 주문 + Outbox를 단일 트랜잭션으로 저장
4. Relay가 Outbox를 Kafka로 발행
5. Inventory/Payment가 이벤트 소비 후 상태 갱신

---

## 5. 데이터 모델(요약)

- `orders`: 주문 헤더(주문ID, 사용자ID, 상태, 총액, 생성시각)
- `order_items`: 주문 라인(상품ID, 수량, 금액)
- `outbox_events`: 이벤트 전파용 테이블(aggregate_id, event_type, payload, status)

인덱스:
- `orders(user_id, created_at desc)`
- `orders(status, created_at)`
- `outbox_events(status, created_at)`

---

## 6. API 예시

### POST `/api/v1/orders`

요청 헤더:
- `Idempotency-Key: <uuid>`

성공 응답:
```json
{
  "orderId": "ord_123",
  "status": "PENDING_PAYMENT"
}
```

### GET `/api/v1/orders/{orderId}`
- 주문 상태 조회

---

## 7. 핵심 설계 결정

1. 주문/이벤트 전파: Outbox 패턴 채택
- 이유: DB 커밋과 이벤트 발행 간 원자성 확보

2. 최종 일관성 허용
- 주문 생성 직후 결제/재고 반영은 비동기 처리
- 사용자에게 상태 기반 UX 제공

3. 멱등성 키 강제
- 네트워크 재시도 시 중복 주문 방지

---

## 8. 장애 시나리오

- Payment Service 장애
  - 영향: 주문이 `PENDING_PAYMENT`로 잔류
  - 대응: 재시도 + DLQ + 운영 알람

- Kafka 지연
  - 영향: 상태 반영 지연
  - 대응: lag 모니터링, 소비자 오토스케일

- Redis 장애
  - 영향: 캐시 미스 증가
  - 대응: DB fallback, 캐시 워밍, 서킷 브레이커

---

## 9. 관측성 지표

필수 메트릭:
- 주문 생성 성공률/실패율
- API p95/p99 latency
- Outbox 적체량
- Kafka consumer lag
- 결제 실패율, 재시도 횟수

필수 로그/추적:
- `orderId`, `idempotencyKey`, `traceId`

---

## 10. 확장 포인트

- 주문 조회 Read Replica 분리
- 상품/지역 기반 샤딩
- Saga 오케스트레이션 도입
- 아카이빙(오래된 주문 cold storage 이동)
