## API Gateway (단일 진입점)
- 필요성 : 공통성격(인증/인가, 로깅, API 변환 등..)의 로직 처리
- 엔드포인트가 변경되더라도, API Gateway에서만 변경하면 되므로, 클라이언트에 영향을 주지 않음

### Spring Cloud Gateway
- OAuth (소셜 로그인) 인증 
  - 내부 API 사용 권한은 OAUTH로 인증
  - 고객별 Data 사용권한 확인(인가)는 JWT 토큰 사용


### BFF(Backend For Frontend) 서버 + GraphQL
- 한번의 요청으로 여러개의 서비스를 호출해야 하는 경우, API Gateway에서 처리
- GraphQL은 BFF의 연장선으로 각 Client의 1 Call에 대한 가장 효율적인 Backend Call을 최적화 해주는 도구