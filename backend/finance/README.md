# Finance

금융 도메인 특유의 기술 주제를 정리한 디렉토리입니다.

## 📚 Contents

### [money_precision.md](./money_precision.md)
- **BigDecimal**: 정밀도 손실 없는 금액 연산
- **IEEE 754 부동소수점 문제**: double/float 사용 시 발생하는 오차
- **Long overflow**: 정수형 금액 표현의 한계
- **DECIMAL(19,4)**: DB 금액 컬럼 타입 설계 기준

### [idempotency_keys.md](./idempotency_keys.md)
- **Idempotency-Key**: 결제/송금/출금 API 중복 처리 방지
- **요청 해시 검증**: 같은 키로 다른 payload가 들어오는 오용 차단
- **DB/Redis 저장 전략**: TTL, 유니크 제약, 처리 상태 관리
- **금융 장애 시나리오**: 타임아웃, 재시도, 중복 결제 방지

### [ledger_double_entry.md](./ledger_double_entry.md)
- **이중부기 원장**: 차변/대변 기반 금전 이동 기록
- **불변 원장**: 수정/삭제 대신 보정 거래로 정합성 유지
- **잔액 스냅샷**: 원장 기반 재생성 가능한 조회 모델
- **Reconciliation**: 외부 PG/은행과 내부 원장 불일치 감지

## 확장 예정 주제
- **환율/통화 전환**: 다중 통화 처리 및 정밀도 관리
- **정산 배치**: 일/월 정산 프로세스 설계

## 분류 기준
이 디렉토리에는 **돈/결제/송금/정산 등 도메인 로직에 종속적인 기술**만 배치합니다.

- 일반 Java BigDecimal 문법 → [`java/`](../../java)
- 결제 API 호출 타임아웃 등 HTTP/TCP 일반 개념 → [`backend/network/`](../network)
- 결제 서비스 간 분산 트랜잭션 패턴 → [`backend/msa/`](../msa)

## 관련 문서
- [/backend/msa](../msa) - 분산 트랜잭션, SAGA, Outbox 패턴
- [/backend/network](../network) - 타임아웃, HTTP 통신
- [/db](../../db) - 트랜잭션, 락, DB 설계
