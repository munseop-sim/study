# Spring Configuration

## `@Value` vs `@ConfigurationProperties`
- `@Value`: 단일 값 주입, 유연 바인딩(Relaxed Binding) 제한적
- `@ConfigurationProperties`: 여러 값을 타입 안전하게 바인딩, 유연 바인딩 지원

## 데이터베이스 커넥션 풀
- 요청마다 연결 생성/종료를 반복하면 성능 저하 및 DB 연결 한도 초과 위험
- 커넥션 풀을 통해 재사용하며 비용을 줄임
- 스레드 풀/커넥션 풀 균형이 중요하고, 과도한 풀 사이즈는 오히려 성능 저하를 유발
