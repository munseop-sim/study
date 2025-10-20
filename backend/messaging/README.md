# Messaging

메시지 브로커 및 이벤트 기반 시스템에 대한 내용을 정리한 디렉토리입니다.

## 📚 Contents

### [01.카프카_설치.md](./01.카프카_설치.md)
Kafka 설치 및 초기 설정 방법

### [02.카프카_소개.md](./02.카프카_소개.md)
Kafka 기본 개념 및 아키텍처
- Producer, Consumer, Broker
- Topic, Partition
- Consumer Group

### [03.카프카_심화.md](./03.카프카_심화.md)
Kafka 고급 주제
- 메시지 전달 보장 (At-most-once, At-least-once, Exactly-once)
- Replication 및 고가용성
- 파티션 전략
- 성능 최적화

## 관련 문서
- [/backend/msa/transaction.md](../msa/transaction.md) - SAGA 패턴에서의 메시징 활용
- [/backend/concurrency](../concurrency) - 메시지 큐를 통한 동시성 제어
