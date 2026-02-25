# Web Fundamentals

HTTP/Web 기반 백엔드 개발에서 자주 다루는 핵심 개념을 정리한 문서입니다.

## 멱등성 (Idempotency)
- 멱등성: 동일한 연산을 여러 번 요청(적용)해도 결과가 달라지지 않는 성질
- 대표적인 멱등 메서드
  - `GET`
  - `HEAD`: 응답 본문 없이 헤더만 반환
  - `PUT`
  - `DELETE`
  - `TRACE`: 요청 경로 디버깅용 (보안 이슈로 비활성화되는 경우가 많음)
  - `OPTIONS`: 허용 HTTP 메서드/프리플라이트 확인
- 실무에서는 고유 식별자(Idempotency Key)로 중복 요청을 제어

## CORS (Cross Origin Resource Sharing)
- 출처가 다른 리소스를 요청할 때 접근 권한을 제어하는 메커니즘
- 출처는 프로토콜 + 도메인 + 포트를 모두 포함
- 허용되지 않은 출처에서 요청하면 브라우저에서 CORS 에러 발생

### 동작 방식
1. Simple Request
- 브라우저가 `Origin`과 `Access-Control-Allow-Origin`을 비교

2. Preflight Request
- 본 요청 전 `OPTIONS` 요청으로 허용 정책 확인
- `Access-Control-Request-Method`, `Access-Control-Request-Headers`를 사용
- 서버는 `Access-Control-Allow-Methods`, `Access-Control-Allow-Headers`로 응답
- `Access-Control-Max-Age`로 프리플라이트 캐시 가능

3. Credential Request
- 쿠키/토큰 포함 요청
- 서버에서 `Access-Control-Allow-Credentials: true` 필요
- 이때 `Access-Control-Allow-Origin`에 `*` 사용 불가

## SSR vs CSR
- SSR (Server Side Rendering)
  - 서버에서 HTML을 완성해 응답
  - SEO에 유리하고 초기 렌더링이 빠름
- CSR (Client Side Rendering)
  - 브라우저가 JS를 받아 동적으로 화면 구성
  - 초기 로딩은 느릴 수 있지만 이후 화면 전환이 빠름

## 웹서버와 WAS
- 웹서버 (Nginx, Apache)
  - 정적 리소스 제공
  - 리버스 프록시, 로드 밸런싱, 캐싱/압축, HTTPS 종료
- WAS (Tomcat 등)
  - 비즈니스 로직 수행
  - 동적 콘텐츠 생성
- 분리 운영 이점
  - 역할 분담으로 자원 효율화
  - 트래픽 특성에 따른 독립적 확장
  - 장애 격리와 운영 유연성 향상

## 참고 자료
- [토스-멱등성이 뭔가요?](https://velog.io/@tosspayments/%EB%A9%B1%EB%93%B1%EC%84%B1%EC%9D%B4-%EB%AD%94%EA%B0%80%EC%9A%94)
- [HTTP 메서드에서 멱등성이란 무엇인가요?](https://www.maeil-mail.kr/question/90)
- [결제창에서 CORS 대응하기](https://docs.tosspayments.com/blog/payment-window-cors-error#%EA%B2%B0%EC%A0%9C%EC%B0%BD%EC%97%90%EC%84%9C-cors-%EB%8C%80%EC%9D%91%ED%95%98%EA%B8%B0)
- [CORS란?](https://tecoble.techcourse.co.kr/post/2020-07-18-cors/)
- [HTTPS에 대해서 설명해주세요.](https://www.maeil-mail.kr/question/106)
