# 네트워크 보안 및 인증

인증, 암호화, 웹 보안과 관련된 핵심 개념을 정리한 문서입니다.

---

## HTTPS 동작 방식

### HTTP vs HTTPS

- **HTTP**: 웹에서 클라이언트와 서버 간 통신 규약. 암호화되지 않은 평문 데이터를 전송하므로 제 3자가 정보를 조회할 수 있는 위험 존재
- **HTTPS**: HTTP에 데이터 암호화가 추가됨. 암호화된 데이터를 전송하여 제 3자 차단

### HTTPS 적용 방법

인증된 기관(Certificate Authority, CA)에서 인증서를 발급받아야 합니다.

1. 서버가 CA에 인증서 요청
2. CA는 CA 이름, 서버의 공개키, 서버 정보를 활용해 인증서 생성
3. CA가 서버 인증서에 전자서명을 생성하여 서버로 전송 (CA 개인 키로 서명)
4. 서버가 인증서를 발급받으면 HTTPS 적용 가능

### TLS 핸드셰이크 (HTTPS 동작 원리)

1. **클라이언트 → 서버**: 암호화 알고리즘, 프로토콜 버전, 무작위 값 전달
2. **서버 → 클라이언트**: 암호화 알고리즘, 인증서, 무작위 값 전달
3. **클라이언트**: 서버 인증서의 서명을 CA(또는 중간 CA) 공개키로 검증하고 신뢰 체인/도메인 유효성 확인
4. **클라이언트**: 클라이언트와 서버의 무작위 값을 조합해 Pre Master Secret 생성 후 서버 공개키로 암호화하여 전달
5. **서버**: 개인 키로 복호화하여 Pre Master Secret 획득
6. **양측**: Pre Master Secret → Master Secret → 세션 키 생성
7. **이후**: 세션 키를 활용한 **대칭키 암호화** 방식으로 데이터 송수신

---

## 대칭키 / 비대칭키 암호화

### 대칭키 암호화 (Symmetric Key Cryptography)

암복호화에 사용하는 키가 동일한 방식.

- **장점**: 비대칭키 암호화에 비해 속도가 빠름
- **단점**:
  - 키 교환 과정에서 탈취 위험 존재
  - 통신 대상이 많아질수록 키 수가 많아져 관리가 복잡

### 비대칭키 암호화 / 공개키 암호화 (Asymmetric Key Cryptography)

암복호화에 사용하는 키가 서로 다른 방식. **공개키**와 **개인키** 존재.

**암호화 목적 사용**:
- 송신자가 수신자의 공개키로 암호화
- 수신자가 개인키로 복호화
- 대칭키 암호화의 키 교환 문제 해결

**서명/검증 목적 사용**:
- 개인키로 데이터를 암호화 (서명)
- 공개키로 복호화하여 검증
- 암호화를 수행한 자에 대한 검증 용도

- **단점**: 대칭키 암호화에 비해 상대적으로 느림

> HTTPS는 TLS 핸드셰이크 단계에서 비대칭키 암호화를 사용해 세션 키를 교환하고, 이후 실제 데이터 전송은 대칭키 암호화로 처리합니다.

---

## CSRF (Cross-Site Request Forgery)

### 개념

사용자가 자신의 의지와 상관없이 공격자가 의도한 행위를 특정 웹사이트에 요청하도록 하는 공격.

### 공격 과정

1. 사용자가 서비스에 로그인 → 서버가 세션 ID를 쿠키로 발급
2. 브라우저는 이후 요청마다 쿠키(세션 ID)를 자동 전달
3. 공격자가 악성 스크립트가 담긴 페이지로 사용자를 유도 (악성 메일, 게시글, 링크 등)
4. 사용자가 악성 페이지 접속 → 사용자 의도와 무관한 요청이 공격 대상 서버로 전송
5. 요청에 세션 ID 쿠키가 자동 포함되어 서버는 정상 요청으로 처리

```html
<!-- 공격자 사이트 내 악성 태그 예시 -->
<img src="https://example.com/member/changePassword?newValue=1234" />
```

### 방어 방법

1. **Referer 헤더 검증**: Referer 헤더와 Host 헤더를 비교하여 교차 출처 요청 차단. 단, Referer는 조작 가능하다는 한계 존재
2. **CSRF 토큰**: 사용자 세션에 임의의 CSRF 토큰 저장. 제출 폼에 해당 토큰값 포함. 요청 시 서버에서 토큰 일치 여부 검증

   ```html
   <input type="hidden" name="csrf_token" value="csrf_token_12341234" />
   ```

3. **SameSite 쿠키**: 크로스 사이트에 대한 쿠키 전송 제어
4. **CORS 설정은 CSRF의 직접적인 방어 수단이 아님**: CORS는 교차 출처 리소스 공유 정책이며, CSRF 방어는 CSRF 토큰/SameSite/Origin 검증 중심으로 구성

---

## JWT (JSON Web Token)

### 개념

통신 정보를 JSON 형식으로 안전하게 전송하기 위해 사용되는 클레임 기반 토큰. 주로 인증과 인가를 구현하기 위해 사용됩니다.

### 구조

- **헤더(Header)**: 토큰의 암호화 알고리즘, 타입
- **페이로드(Payload)**: 데이터 (만료일, 사용자 정보 등)
- **시그니처(Signature)**: 헤더와 페이로드를 비밀 키로 암호화. 헤더+페이로드의 변조 여부 판단에 사용

### 장점

- 토큰 자체에 정보 포함 → 세션 기반 대비 사용자 정보 조회를 위한 추가 작업 불필요
- 서버가 상태를 관리하지 않아(stateless) 다중 서버 환경에서도 세션 불일치 문제 없음

### 주의 사항

- **디코딩 용이**: Base64로 쉽게 디코딩 가능하므로 민감 정보를 페이로드에 담지 말 것
- **브루트포스 공격**: 시크릿 키 복잡도가 낮으면 무작위 대입 공격에 노출. 강력한 시크릿 키 사용 권장
- **시크릿 키 관리**: 유출되지 않도록 안전한 공간에서 관리 필수
- **JWT 탈취 대응**: JWT 저장 공간, 리프레시 토큰 도입, Refresh Token Rotation, 탈취 감지 및 대응 고려
- **사용자 경험**: 토큰 만료로 인한 사용자 불편 고려 (슬라이딩 세션 전략 검토)
- **none 알고리즘 공격**: 헤더의 알고리즘을 none으로 변경해 시그니처 검증 우회 가능. none 알고리즘 및 약한 알고리즘 필터링 필요

---

## CORS (Cross-Origin Resource Sharing)

### 개념

출처가 다른 곳의 리소스를 요청할 때 접근 권한을 부여하는 메커니즘. 출처는 프로토콜 + 도메인 + 포트를 모두 포함합니다.

### 등장 배경

- 과거 CSRF 문제를 예방하기 위해 브라우저에 **SOP(Same-Origin Policy, 동일 출처 정책)** 도입
- 현대 웹은 다른 출처의 리소스를 사용하는 경우가 많아 SOP를 확장한 CORS가 필요

### 동작 방식

브라우저가 요청의 `Origin` 헤더와 응답의 `Access-Control-Allow-Origin` 헤더를 비교하여 CORS 위반 여부 확인.

#### 1. Simple Request (단순 요청)

조건: GET, POST, HEAD 메서드 + 특정 헤더/Content-Type인 경우
- 브라우저가 `Origin` 헤더 포함하여 요청 전송
- 서버가 `Access-Control-Allow-Origin`으로 허용 출처 응답

#### 2. Preflight Request (사전 요청)

본 요청 전 `OPTIONS` 메서드로 안전한지 확인하는 사전 요청.

요청 헤더:
- `Access-Control-Request-Method`: 실제 요청 메서드
- `Access-Control-Request-Headers`: 실제 요청 추가 헤더 목록

응답 헤더:
- `Access-Control-Allow-Methods`: 허용 메서드
- `Access-Control-Allow-Headers`: 허용 헤더
- `Access-Control-Max-Age`: Preflight 캐시 기간

#### 3. Credential Request (인증 요청)

쿠키나 토큰 같은 인증 정보가 포함된 요청.

- 서버에서 `Access-Control-Allow-Credentials: true` 설정 필요
- `Access-Control-Allow-Origin`에 와일드카드(`*`) 사용 불가

---

## 쿠키 vs 세션

HTTP의 **무상태(stateless)** 특성을 보완하여 사용자 상태를 유지하는 메커니즘입니다.

| 항목 | 쿠키 | 세션 |
|------|------|------|
| 저장 위치 | 클라이언트(브라우저) | 서버 |
| 보안성 | 사용자가 직접 접근/수정 가능 → 취약 | 중요 정보가 서버에 저장 → 상대적으로 안전 |
| 용량 | 도메인별 약 4KB | 서버 리소스에 따라 다름 (쿠키보다 많음) |
| 라이프사이클 | 개발자가 설정한 만료 시간. 미설정 시 브라우저 닫을 때 삭제 | 서버 설정에 따라 관리. 일정 시간 요청 없으면 만료 |
| 성능 영향 | 모든 HTTP 요청에 함께 전송 → 많을수록 트래픽 증가 | 서버 메모리 사용 → 세션 많을수록 서버 부하 증가 |

### 세션 동작 방식

1. 서버에 세션 데이터 저장
2. 세션 ID만 쿠키를 통해 클라이언트에 전달
3. 클라이언트는 요청 시 세션 ID 쿠키를 자동으로 전송

### 사용 지침

- **비민감 정보** (사용자 선호 설정, 비로그인 장바구니): 쿠키
- **중요 데이터** (로그인 정보): 세션
- 최근에는 JWT와 같은 토큰 기반 인증 방식도 많이 사용

---

## 다중 서버 세션 문제

### 세션 불일치 문제

다중 서버 환경(A, B 서버)에서 로드밸런서가 요청을 분산할 때:

1. 첫 번째 요청이 서버 A로 → 세션 정보가 A에 저장
2. 이후 요청이 서버 B로 → 세션 정보 없음 → 요청 처리 불가

### 해결 방법

#### 1. 스티키 세션 (Sticky Session)

사용자 요청이 항상 세션이 저장된 서버로 가도록 고정. 쿠키나 IP로 서버 결정.

- **장점**: 구현이 단순
- **단점**:
  - 특정 서버에 트래픽 집중 가능
  - 해당 서버 다운 시 세션 유실 → 재로그인 필요

#### 2. 세션 클러스터링 (Session Clustering)

세션 정보가 생성될 때 다른 서버로 복제하는 방식.

- **장점**: 트래픽 몰림과 세션 유실 문제 해결
- **단점**:
  - 중복 저장으로 메모리 비효율
  - 복제 과정에서 네트워크 트래픽 증가
  - 복제 지연으로 일시적 세션 유실 가능

#### 3. 스토리지 분리 (External Session Storage)

세션 정보를 외부 스토리지(Redis 등)로 분리하는 방식.

- **장점**: 스티키 세션, 클러스터링의 문제를 해결
- **단점**:
  - 외부 스토리지에 대한 단일 장애 지점(SPOF) 발생 가능
  - HA 구성으로 SPOF를 해소해도 복제 지연 문제는 잔존
  - 외부 스토리지 관리를 위한 추가 리소스 필요

---

## 참고 자료

- [리니의 HTTPS - 10분 테코톡](https://youtu.be/8BNx8UbAo2s?si=sJoKiHwNwWVFn_to)
- [알린의 암호 - 10분 테코톡](https://youtu.be/UJDB6e8s1Fg?feature=shared)
- ["Same-site" and "same-origin" - web.dev](https://web.dev/articles/same-site-same-origin)
- [MDN - 동일 출처 정책](https://developer.mozilla.org/ko/docs/Web/Security/Same-origin_policy)
- [CSRF Token에 관하여](https://www.xn--hy1b43d247a.com/critical-info-infrastructure/01-account-management/csrf-token)
- [브라우저 쿠키와 SameSite 속성](https://seob.dev/posts/%EB%B8%8C%EB%9D%BC%EC%9A%B0%EC%A0%80-%EC%BF%A0%ED%82%A4%EC%99%80-SameSite-%EC%86%8D%EC%84%B1/)
- [직접 만들어보며 이해하는 JWT - hudi.blog](https://hudi.blog/self-made-jwt/)
- [Refresh Token과 Sliding Sessions를 활용한 JWT의 보안 전략](https://blog.ull.im/engineering/2019/02/07/jwt-strategy.html)
- [Auth0 - Refresh Token Rotation](https://auth0.com/docs/secure/tokens/refresh-tokens/refresh-token-rotation)
- [Hacking JWT : Exploiting the "none" algorithm](https://medium.com/@phosmet/forging-jwt-exploiting-the-none-algorithm-a37d670af54f)
- [토스페이먼츠 - CORS 대응하기](https://docs.tosspayments.com/blog/payment-window-cors-error)
- [MDN - 교차 출처 리소스 공유 (CORS)](https://developer.mozilla.org/ko/docs/Web/HTTP/CORS)
- [테코블 - CORS란?](https://tecoble.techcourse.co.kr/post/2020-07-18-cors/)
- [다중 서버 환경에서의 세션 불일치 문제와 해결방법 - hudi.blog](https://hudi.blog/session-consistency-issue/)
- [쿠키와 세션의 차이점 및 보안 고려사항 - F-Lab](https://f-lab.kr/insight/cookie-vs-session)
