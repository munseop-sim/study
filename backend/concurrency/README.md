# Concurrency Control

동시성 제어 및 병렬 처리에 대한 내용을 정리한 디렉토리입니다.

## 📚 Contents

### [concurrency_control.md](./concurrency_control.md)

#### 1. RDB를 통한 동시성 제어
- **낙관적 락 (Optimistic Lock)**: `@Version` 어노테이션 활용
- **비관적 락 (Pessimistic Lock)**: DB 레벨 락 사용
- **트랜잭션 격리 수준**
  - READ UNCOMMITTED
  - READ COMMITTED
  - REPEATABLE READ
  - SERIALIZABLE

#### 2. Redis를 통한 분산 락
- `setNx` 명령어를 활용한 분산락 구현
- Single Thread 기반의 동시성 제어

#### 3. Spring에서의 동시성 제어
- JPA `@Version` 어노테이션 (낙관적 락)

#### 4. 메시지 큐 활용
- RabbitMQ, Kafka를 활용한 순차 처리

### [toctou.md](./toctou.md)

#### TOCTOU (Time-of-Check-Time-of-Use)
- **정의**: 검사 시점(Check)과 사용 시점(Use) 사이에 상태가 변경되는 Race Condition의 하위 유형
- **발생 유형**
  - 파일 시스템 TOCTOU (보안 취약점, CWE-367)
  - 비즈니스 로직 TOCTOU (잔액/재고 차감, 멱등성 검사)
  - 인증/권한 TOCTOU
- **해결 패턴**
  - 원자적 연산 (Atomic Operation)
  - 비관적 락 (Pessimistic Lock)
  - 낙관적 락 (Optimistic Lock)
  - Double-Check Locking
  - CAS (Compare-And-Swap)
- **실무 체크리스트**: 코드 리뷰 시 TOCTOU 의심 패턴

## 관련 문서
- [/db/README.md](../../db/README.md) - DB Lock 상세 설명
- [/spring/transaction.md](../../spring/transaction.md) - Spring 트랜잭션
