# JVM Runtime

## JVM 구조
- 클래스 로더: 클래스 파일 로딩
- 런타임 메모리 영역
  - Stack: 스레드별 호출 정보/지역변수
  - Heap: 객체/배열 저장 (GC 대상)
  - PC Register: 현재 실행 명령 위치
  - Native Method Stack
- 실행 엔진
  - 인터프리터
  - JIT
  - GC
