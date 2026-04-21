# Kotlin

Kotlin 언어 학습 내용을 주제별로 분류한 인덱스입니다.

## 📚 Contents

### Coroutines
- [coroutines.md](./coroutines.md): 코루틴 종합 정리 — CoroutineScope, Dispatcher, Flow, 채널, 구조적 동시성

## 예약 주제 (`java/`의 4분할 구조와 대응)

| Java 분류 | Kotlin 대응 예정 주제 |
|---|---|
| Runtime / JVM | Kotlin 컴파일 타겟 (JVM / JS / Native), inline 함수, reified 타입 |
| Language Basics | data class, sealed class, extension function, 널 안전성, 스코프 함수 |
| Concurrency | Coroutines (Flow, Channel, structured concurrency) |
| Error Handling | `runCatching`, Result 타입, `Either` 패턴 |

## 분류 기준
이 디렉토리에는 **Kotlin 언어/런타임/표준 라이브러리** 주제만 배치합니다.

- Spring Boot Kotlin 코드 (빈 등록, 트랜잭션, MVC 설정 등) → [`spring/`](../spring)
- 코루틴으로 풀어낸 분산 처리 패턴 → [`backend/concurrency/`](../backend/concurrency) 또는 [`backend/msa/`](../backend/msa)

## 관련 문서
- [/java](../java) - Java 언어 (병렬 슬롯)
- [/spring](../spring) - Spring Boot Kotlin 통합
- [/backend/concurrency](../backend/concurrency) - 애플리케이션 레벨 동시성 제어
