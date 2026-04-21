# Network

네트워크 프로토콜 및 통신 관련 내용을 정리한 디렉토리입니다.

## 📚 Contents

### [README.md](./README.md)

#### HTTP & TCP
- **HTTP Header**: Key-Value 형태의 요청 메타데이터
- **TCP 3-Way Handshake**: 연결 설정 과정 (SYN → SYN+ACK → ACK)
- **TCP 4-Way Handshake**: 연결 종료 과정 (FIN → ACK → FIN → ACK)
- **TLS Handshake**: 보안 연결 설정

#### 네트워크 타임아웃
- **Connection Timeout**: TCP 연결 수립 타임아웃
- **Socket Timeout**: 패킷 간 전송 시간 제한
- **Read Timeout**: I/O 작업 완료 타임아웃
- 타임아웃의 필요성: 자원 절약 및 장애 예방

#### 웹사이트 접속 과정
1. 도메인 입력
2. DNS를 통한 IP 주소 획득
3. TCP 3-Way Handshake (HTTPS의 경우 TLS Handshake)
4. HTTP 요청/응답 (80/443 포트)
5. TCP 4-Way Handshake (연결 종료)

### [Proxy.md](./Proxy.md)

#### Proxy 서버
프록시 서버의 필요성과 종류
- **Forward Proxy**: 클라이언트 측 프록시
  - 캐싱 서버
  - 특정 사이트 접근 차단
- **Reverse Proxy**: 서버 측 프록시
  - Load Balancing
  - 무중단 배포
  - SSL 암호화

#### Nginx
- Reverse Proxy 구성
- Load Balancing 알고리즘 (Round-Robin, IP Hash)
- Kubernetes Ingress Controller

#### HAProxy
- L4/L7 스위치 대체 솔루션
- 통계 정보, SSL 지원
- Active Health Check
- KeepAlived (프록시 이중화)

### [web_fundamentals.md](./web_fundamentals.md)
- HTTP 멱등성
- CORS
- SSR vs CSR
- 웹서버 vs WAS

### [http_tcp_timeout_layers.md](./http_tcp_timeout_layers.md)
HTTP/TCP 계층별 프로토콜과 4계층 타임아웃 캐스케이드 설계
- **계층별 프로토콜 스택**: TCP → HTTP → 애플리케이션 계층 타임아웃 흐름
- **4계층 타임아웃**: Connection / Socket / Read / Write Timeout 역할 구분
- **캐스케이드 설계**: 각 계층 타임아웃 값의 연쇄 영향 및 설정 가이드

## 관련 문서
- [/backend/msa/api_gateway.md](../msa/api_gateway.md) - API Gateway 패턴
