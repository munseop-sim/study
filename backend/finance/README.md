# Finance

금융 도메인 특유의 기술 주제를 정리한 디렉토리입니다.

## 📚 Contents

### [money_precision.md](./money_precision.md)
- **BigDecimal**: 정밀도 손실 없는 금액 연산
- **IEEE 754 부동소수점 문제**: double/float 사용 시 발생하는 오차
- **Long overflow**: 정수형 금액 표현의 한계
- **DECIMAL(19,4)**: DB 금액 컬럼 타입 설계 기준

## 확장 예정 주제
- **이중부기 (Double-Entry Bookkeeping)**: 차변/대변 기반 장부 설계
- **멱등성 키 설계**: 결제/송금 요청 중복 방지 패턴
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
