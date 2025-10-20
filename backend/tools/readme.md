# Development Tools

개발 생산성 향상을 위한 도구와 기본 개념들을 정리한 디렉토리입니다.

## 📚 Contents

### [productivity](./productivity)
개발 생산성 도구 모음

#### [git.md](./productivity/git.md)
Git 버전 관리 시스템
- 기본 명령어
- 브랜치 전략
- 협업 워크플로우

#### [intellij_shortcut_mac.md](./productivity/intellij_shortcut_mac.md)
IntelliJ IDEA 단축키 (macOS)

#### [intellij_shortcut_window.md](./productivity/intellij_shortcut_window.md)
IntelliJ IDEA 단축키 (Windows)

## 개발 기본 개념

### 멱등성 (Idempotency)
- 분산 시스템에서는 고유한 식별자를 사용하여 멱등성을 보장하는 방법이 일반적
- 메시지 브로커 시스템에서는 메시지를 전송할 때 고유한 메시지 ID를 부여하고, 수신측에서는 이 ID를 확인하여 중복 메시지를 처리하지 않음
- [HTTP 메서드에서 멱등성이란 무엇인가요?](https://www.maeil-mail.kr/question/90)

### 참고 자료
- [로그와 메트릭을 설명](https://www.maeil-mail.kr/question/66)
- [equals, hashCode 재정의](https://www.maeil-mail.kr/question/70)
- [동일성과 동등성에 대해서 설명](https://www.maeil-mail.kr/question/71)
- [동기방식으로 외부서비스를 호출할 때 외부서비스 장애가 나면 어떻게 조치할까?](https://www.maeil-mail.kr/question/74)
- [동기, 비동기 차이점](https://www.maeil-mail.kr/question/77)
- [단위테스트와 통합테스트의 차이점](https://www.maeil-mail.kr/question/83)
- [스레드, 프로세스, 코어의 수는 많을 수록 좋을까?](https://www.maeil-mail.kr/question/84)

### CORS(Cross Origin Resource Sharing)
- 출처가 다른 곳의 리소스를 요청할 때 접근 권한을 부여하는 메커니즘
- `출처가 교차`: 리소스를 주고받는 두 곳의 출처가 다를 때를 말함. 
  - 출처: URL뿐만 아니라 프로토콜과 포트까지 포함
  - 만약 클라이언트의 출처가 허용되지 않았다면 CORS 에러가 발생할 수 있음.
- CORS 정책이 필요한 이유: CSRF를 예방하기 위해 브라우저는 동일 출처 정책(SOP, same-origin policy)을 구현하였으나, 현대의 웹어플리케이션은 출처가 다른 리소스를 사용하는 경우가 많음
- 작동방식
  1. Simple Request
     - 브라우저가 요청 메시지에 Origin 헤더와 응답 메시지의 Access-Control-Allow-Origin 헤더를 비교해서 CORS를 위반하는지 확인
     - 이때, Origin에는 현재 요청하는 클라이언트의 출처(프로토콜, 도메인, 포트)가, Access-Control-Allow-Origin은 리소스 요청을 허용하는 출처가 작성
     - Simple Request은 요청 메서드(GET, POST, HEAD), 수동으로 설정한 요청 헤더(Accept, Accept-Language, Content-Language, Content-Type, Range), Content-Type 헤더(application/x-www-form-urlencoded, multipart/form-data, text/plain)인 경우에만 해당
  2. Preflight Request
     - 브라우저가 사전 요청을 보내는 경우
     - 브라우저가 본 요청을 보내기 이전, Preflight Request를 OPTIONS 메서드로 요청을 보내어 실제 요청이 안전한지 확인.
     - Preflight Request는 추가로 Access-Control-Request-Method로 실 요청 메서드와, Access-Control-Request-Headers 헤더에 실 요청의 추가 헤더 목록을 담아서 보내야 함.
     - 이에 대한 응답은 대응되는 Access-Control-Allow-Methods와 Access-Control-Headers를 보내야 하고, Preflight Request로 인한 추가 요청을 줄이기 위해 캐시 기간을 Access-Control-Max-Age에 담아서 보내야 함.
  3. Credential Request
     - 인증된 요청을 사용하는 방식
     - 쿠키나 토큰과 같은 인증 정보를 포함한 요청은 더욱 안전하게 처리되어야 함
     - Credential Request를 요청하는 경우에는 서버에서는 Access-Control-Allow-Credentials를 true로 설정해야 하며 Access-Control-Allow-Origin에 와일드카드를 사용하지 못함
- 참고자료
  - [결제창에서 CORS 대응하기](https://docs.tosspayments.com/blog/payment-window-cors-error#%EA%B2%B0%EC%A0%9C%EC%B0%BD%EC%97%90%EC%84%9C-cors-%EB%8C%80%EC%9D%91%ED%95%98%EA%B8%B0)
  - [CORS란?](https://tecoble.techcourse.co.kr/post/2020-07-18-cors/)


### WAS, 웹서버 차이점
- 웹서버(WebServer)
  - 정적 컨텐츠(HTML, CSS, JS, 이미지 등)를 제공하는 역할 (정적컨텐츠 제공에 특화)
  - 동적 컨텐츠 요청시에 요청을 WAS로 전달
  - Nginx, Apache 등이 있음
- WAS(WebApplicationServer)
  - 동적처리가 필요한 요청 처리(동적 컨텐츠 생성과 데이터처리에 특화)
  - 애플리케이션 로직 수행
  - tomcat..
- **WAS에서도 정적인 처리가 가능한데 웹서버를 따로 두는 이유**
  - WAS가 너무 많은 역할을 담당하면 과부하될 수 있음. 
  - 웹 서버를 따로 분리하면 WAS는 중요한 애플리케이션 로직에 집중할 수 있으며, 웹 서버는 정적 리소스를 처리하면서 업무 분담이 가능 &rarr; 효율적인 자원(시스템 리소스)관리
    - 정적 컨텐츠가 많이 사용되는 경우에는 웹 서버를 증설
    - 애플리케이션 자원이 많이 사용되면 WAS를 증설
  - 로드 밸런싱을 수행, 캐싱 및 압축, HTTPS 등을 웹 서버에서 처리하도록 할 수 있음.

- [HTTPS에 대해서 설명해주세요.](https://www.maeil-mail.kr/question/106)
- [Record를 DTO로 사용하는 이유](https://www.maeil-mail.kr/question/107)