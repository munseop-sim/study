# Concurrency Control

동시성 제어 및 병렬 처리에 대한 내용을 정리한 디렉토리입니다.

## 📚 Contents

### [동시성_제어.md](./동시성_제어.md)

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

## 관련 문서
- [/db/readme.md](../../db/readme.md) - DB Lock 상세 설명
- [/spring/transaction.md](../../spring/transaction.md) - Spring 트랜잭션
