# Java Concurrency

## 스레드 생성 방식
- `Thread` 상속
- `Runnable` 구현

## 비동기 처리 도구
- `Future`: 결과 대기 시 블로킹 가능
- `CompletableFuture`: 콜백 체이닝 기반 비동기 처리
- `ExecutorService`: 스레드 풀/작업 실행 관리

## 동시성 제어
- `Atomic` 타입
- `synchronized`
- `volatile`
- `Lock` 인터페이스
