# Web Fundamentals

HTTP/Web 기반 백엔드 개발에서 자주 다루는 핵심 개념을 정리한 문서입니다.

---

## TCP 3-way Handshake

TCP/IP 네트워크에서 안정적이고 연결 지향적인 통신을 설정하기 위해 사용되는 절차입니다. 클라이언트와 서버 간에 신뢰할 수 있는 연결을 설정하기 위해 세 개의 메시지(세그먼트)를 교환합니다.

### 절차

1. **SYN (클라이언트 → 서버)**: 클라이언트가 연결 요청 SYN 세그먼트를 보냄. 초기 순서 번호(Sequence Number)와 윈도우 크기(Window Size) 정보 포함
2. **SYN+ACK (서버 → 클라이언트)**: 서버가 요청 수락하고 SYN+ACK 플래그 세그먼트를 응답. 서버의 초기 순서 번호와 클라이언트 순서 번호+1에 대한 ACK 포함
3. **ACK (클라이언트 → 서버)**: 클라이언트가 서버 응답 확인하고 ACK 세그먼트 전송. 서버의 초기 순서 번호+1에 대한 ACK 포함

이 절차가 완료되면 신뢰할 수 있는 연결이 설정되고 데이터 전송이 시작됩니다.

### 4-way Handshake (연결 종료)

모든 데이터 전송이 완료되면 클라이언트와 서버는 4-Way Handshake를 거쳐 TCP 연결을 종료합니다.

---

## 웹사이트 접근 과정

사용자가 `www.google.com`을 입력할 때 발생하는 과정:

1. **DNS 질의 (애플리케이션 계층)**: 브라우저가 DNS 서버에 도메인 이름에 대한 IP 주소를 질의. DNS 서버는 IP 주소(예: `142.250.190.78`)를 응답
2. **TCP 3-Way Handshake (전송 계층)**: IP 주소를 얻은 후 구글 서버와 TCP 연결 수립
3. **HTTP Request 전송**: 브라우저가 HTTP Request 메시지 생성 (`GET / HTTP/1.1`). TCP 프로토콜을 통해 80번 포트로 전송. 데이터는 패킷 형태로 전달
4. **네트워크 계층 처리**: IP 주소(3계층), MAC 주소(2계층)를 이용해 패킷 전송
5. **HTTP Response 수신**: 서버가 `200 OK`와 함께 웹 페이지 데이터(HTML, CSS, JS)를 응답
6. **렌더링**: 브라우저가 응답 데이터를 해석해 화면에 페이지 렌더링
7. **TCP 연결 종료**: 4-Way Handshake를 통해 연결 종료

---

## HTTP 멱등성 (Idempotency)

### 개념

동일한 연산을 여러 번 적용하더라도 결과가 달라지지 않는 성질. HTTP 메서드의 멱등성은 동일한 요청을 한 번 보내는 것과 여러 번 보내는 것이 서로 동일한 효과를 지니며, 서버의 상태도 동일하게 남을 경우를 의미합니다.

### 멱등한 메서드

- `GET`: 리소스 조회
- `HEAD`: 응답 본문 없이 헤더만 반환
- `PUT`: 리소스 대체
- `DELETE`: 리소스 삭제
- `TRACE`: 요청 경로 디버깅용
- `OPTIONS`: 허용 HTTP 메서드/프리플라이트 확인

`POST`, `PATCH`는 멱등하지 않습니다.

### 활용

- 전송 커넥션이 끊어졌을 때, 멱등성이 클라이언트가 같은 요청을 재시도해도 되는지에 대한 판단 근거
- 멱등한 결제 API라면 타임아웃 발생 시에도 안심하고 재요청 가능 (중복 결제 방지)
- 실무에서는 고유 식별자(Idempotency Key)로 중복 요청을 제어

---

## HTTP/1.1 vs HTTP/2.0

### HTTP/1.1

- **지속 커넥션(Persistent Connection)**: HTTP/1.0은 요청마다 TCP 커넥션을 새로 생성했으나, 1.1부터는 지정한 타임아웃만큼 커넥션을 유지하여 재사용
- **파이프라이닝(Pipelining)**: 응답을 기다리지 않고 여러 요청을 순차적으로 전송. 모든 응답을 한 번에 대기

#### 문제점

- **Head-of-Line (HOL) Blocking**: 파이프라인에서 첫 번째 요청이 오래 걸리면 나머지 요청이 모두 대기해야 하는 문제
- **헤더 중복**: 매 요청마다 동일한 헤더를 반복 전송

### HTTP/2.0

- **바이너리 프레이밍**: 메시지를 프레임 단위로 분할하고 바이너리 형태로 전송. 파싱 및 전송 속도 향상
- **멀티플렉싱(Multiplexing)**: 하나의 커넥션으로 요청과 응답을 병렬 처리. 각 요청이 독립적으로 처리되어 애플리케이션 레이어의 HOL Blocking 해결
- **HPACK 헤더 압축**: 반복되는 헤더를 효율적으로 관리하여 대역폭 최적화

---

## URI / URL / URN 차이

```
URI (Uniform Resource Identifier)
├── URL (Uniform Resource Locator)
└── URN (Uniform Resource Name)
```

- **URI**: 인터넷에서 자원을 식별하기 위한 문자열. URL과 URN을 포함하는 상위 개념
- **URL**: URI의 한 형태로, 인터넷상에서 자원의 **위치**를 나타내는 방식. 자원에 접근하기 위한 프로토콜 포함
  - 예: `https://www.example.com/path/to/resource`
- **URN**: URI의 또 다른 형태로, 자원의 위치와 관계없이 자원의 **이름**을 식별하는 방식. 자원의 위치가 변해도 동일한 식별자 유지
  - 예: `urn:isbn:0451450523` (특정 책의 ISBN)

---

## Keep Alive

### 개념

네트워크 또는 시스템에서 커넥션을 지속적으로 유지하기 위해 사용되는 기술이나 설정.

### HTTP의 Keep-Alive

하나의 TCP 커넥션으로 여러 개의 HTTP 요청과 응답을 주고받을 수 있도록 하는 기능.
- HTTP/1.0: 요청마다 새로운 커넥션을 열고 닫음
- HTTP/1.1부터: Keep-Alive가 기본 활성화되어 커넥션 재사용 가능
- 일정 시간 동안 요청이 없으면 타임아웃만큼 커넥션 유지 후 종료

### TCP의 Keep-Alive

커넥션이 유휴 상태일 때 커넥션이 끊어지지 않도록 주기적으로 패킷을 전송하는 기능.
- 패킷을 전송해서 커넥션이 살아있음을 확인
- 살아있으면 커넥션을 지속해서 유지

### 장점

- 커넥션 재사용으로 네트워크 비용 절감
- Handshake RTT(Round Trip Time) 감소로 지연 시간 감소
- CPU, 메모리 등 리소스 소비 감소

### 단점

- 유휴 상태에서도 커넥션 점유로 서버 소켓 부족 가능
- DoS 공격으로 서버 과부하 가능
- 타임아웃 설정이 부적절하면 커넥션 리소스 낭비

---

## SSR vs CSR

### SSR (Server Side Rendering)

서버에서 HTML을 완성해 응답하는 방식.

- 클라이언트가 요청하면 서버가 필요한 데이터를 모두 삽입하고 CSS까지 적용한 렌더링 준비된 HTML과 JS를 응답
- 브라우저는 JS를 다운로드하고 HTML에 JS를 연결(Hydration)

**장점**
- 모든 데이터가 HTML에 포함되어 SEO에 유리
- JS 다운로드 전에도 렌더링된 HTML 확인 가능 → 초기 구동 속도 빠름

**단점**
- 서버에 부하 증가
- 페이지 이동 시마다 서버에서 새 HTML을 받아야 함

### CSR (Client Side Rendering)

클라이언트(브라우저)에서 렌더링하는 방식.

- 서버는 빈 뼈대 HTML만 응답
- 클라이언트가 JS 파일을 다운로드해 동적으로 페이지 생성

**장점**
- 초기 로딩 후 페이지 전환이 빠름 (필요한 데이터만 요청)
- 서버 부하가 적음
- 반응속도가 빠르고 UX 우수

**단점**
- 빈 HTML 때문에 SEO에 불리 (웹 크롤러가 색인할 콘텐츠 없음)
- JS 다운로드 및 DOM 생성 시간 대기로 초기 로딩 속도 느림

---

## 회선 교환 vs 패킷 교환

### 회선 교환 (Circuit Switching)

특정 사용자를 위한 회선의 경로를 미리 설정하고, 이 경로를 이용해 호스트끼리 메시지를 주고받는 방식.

- **특징**: 주어진 시간 동안 전송 데이터 양이 비교적 일정하고 안정적
- **단점**: 회선 이용 효율이 떨어짐 (비어있어도 다른 사람이 사용 불가)
- **사례**: 유선 전화망

### 패킷 교환 (Packet Switching)

목적지를 정해두고 메시지를 패킷으로 분할해서 보내고, 목적지에서 패킷을 조립해 확인하는 방식.

- **특징**: 라우터가 패킷을 최적 경로로 전달. 경로는 수시로 변경 가능. 데이터를 전송하는 동안에만 네트워크 자원 사용
- **장점**: 회선 이용 효율이 높음
- **단점**: 경로 탐색에서 지연 발생 가능. 패킷 헤더로 인한 오버헤드 발생

현대 인터넷은 패킷 교환 방식을 사용합니다.

---

## 웹서버와 WAS

- **웹서버 (Nginx, Apache)**
  - 정적 리소스 제공
  - 리버스 프록시, 로드 밸런싱, 캐싱/압축, HTTPS 종료
- **WAS (Tomcat 등)**
  - 비즈니스 로직 수행
  - 동적 콘텐츠 생성
- **분리 운영 이점**
  - 역할 분담으로 자원 효율화
  - 트래픽 특성에 따른 독립적 확장
  - 장애 격리와 운영 유연성 향상

---

## 참고 자료

- [TCP 3 Way-Handshake & 4 Way-Handshake](https://mindnet.tistory.com/entry/%EB%84%A4%ED%8A%B8%EC%9B%8C%ED%81%AC-%EC%89%BD%EA%B2%8C-%EC%9D%B4%ED%95%B4%ED%95%98%EA%B8%B0-22%ED%8E%B8-TCP-3-WayHandshake-4-WayHandshake)
- [웹의 동작 방식 - MDN](https://developer.mozilla.org/ko/docs/Learn/Getting_started_with_the_web/How_the_Web_works)
- [웹페이지를 표시한다는 것: 브라우저는 어떻게 동작하는가 - MDN](https://developer.mozilla.org/ko/docs/Web/Performance/How_browsers_work)
- [토스페이먼츠 - 멱등성이 뭔가요?](https://velog.io/@tosspayments/%EB%A9%B1%EB%93%B1%EC%84%B1%EC%9D%B4-%EB%AD%94%EA%B0%80%EC%9A%94)
- [HTTP 멱등성 개념에서 적용까지](https://rice-honey.tistory.com/8)
- [웹 개발자라면 알고 있어야 할 HTTP의 진화 과정](https://yozm.wishket.com/magazine/detail/1686/)
- [HTTP3까지 버전별 변천사와 동작원리](https://velog.io/@yesbb/HTTP3%EA%B9%8C%EC%A7%80-%EB%B2%84%EC%A0%84%EB%B3%84-%EB%B3%80%EC%B2%9C%EC%82%AC#2-tcp%EB%A0%88%EB%B2%A8%EC%97%90%EC%84%9C%EC%9D%98-holbhead-of-line-blocking-)
- [URI, URL 그리고 URN - hudi.blog](https://hudi.blog/uri-url-urn/)
- [카카오페이 기술 블로그 - URL이 이상해요!](https://tech.kakaopay.com/post/url-is-strange/)
- [MDN Web Docs - Keep-Alive](https://developer.mozilla.org/ko/docs/Web/HTTP/Headers/Keep-Alive)
- [CSR, SSR, SPA, MPA? 상사한테 혼나기 전에 알아야하는 것](https://blog.the-compass.kr/csr-ssr-spa-mpa-ede7b55c5f6f)
