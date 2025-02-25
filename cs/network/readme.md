### Http Header
- Key, Value 형태
- http요청시에 요청 URL, RequestBody에 전송되는 값이 아닌 요청규약, 인증정보등을 가진 데이터

### [Proxy](Proxy.md)

### TCP 3Way HandShake, 4Way HandShake
#### TCP 3-Way HandShake
- 클라이언트와 서버간의 신뢰할 수 있는 연결을 설정하기 위한 과정
- Step1: SYN:: 클라이언트에서 서버로 연결요청 메세지인 SYN을 보냄
- Step2: SYN+ACK:: 서버는 클라이언트의 SYN메세지를 수락하고 응답으로 SYN과 ACK플래그가 설정된 패킷을 송신 
- Step3: ACK:: 클라이언트는 서버의 응답을 수신하고 ACK패킷을 다시 서버로 보냄
#### TCP 4-Way HandShake
- 클라이언트와 서버간의 연결을 종료하기 위한 과정
- STEP1: FIN:: 클라이언트는 연결 종료 요청 메시지인 FIN 패킷을 서버에 보냄
- STEP2: ACK:: 서버는 FIN 요청을 수락하고 ACK 패킷을 클라이언트에 보냄
- STEP3: FIN:: 서버는 자신의 데이터 전송이 끝났음을 알리는 FIN 패킷을 클라이언트에 보냄
- STEP4: ACK:: 클라이언트는 FIN 패킷을 확인한 후 ACK 패킷을 다시 서버로 보냄

### TLS HANDSHAKE
- todo 정리 필요

### Connection Timeout, Socket Timeout, Read Timeout의 차이점
- Connection timeout
  - 클라이언트가 서버에 연결을 시도할 때, 일정 시간 내에 연결이 이루어지지 않으면 발생하는 타임아웃
  - TCP 소켓 통신에서 클라이언트와 서버가 연결될 때, 정확한 전송을 보장하기 위해 사전에 세션을 수립하는데, 이 과정을 [TCP 3-Way HandShake](#TCP-3-Way-HandShake)
  - `Connection Timeout`은 이 [TCP 3-Way HandShake](#TCP-3-Way-HandShake)가 시간 내에 완료되지 않을 때 발생
  - 즉, 서버의 장애나 응답 지연으로 인해 연결을 맺지 못하면 Connection Timeout이 발생
- Socket timeout
  - Connection Timeout 이후에 발생할 수 있는 타임아웃
  - 클라이언트와 서버가 연결된 후, 서버는 데이터를 클라이언트에게 전송한다. 이때 하나의 데이터 덩어리가 아니라 여러 개의 패킷 단위로 쪼개서 전송되는데, 각 패킷이 전송될 때의 시간 차이 제한을 `Socket Timeout`이라고 함
  - 만약 서버가 일정 시간 내에 다음 패킷을 보내지 않으면, 클라이언트는 `Socket Timeout`을 발생시키고 연결을 종료할 수 있음.
- Read timeout
  - 클라이언트와 서버가 연결된 후, 특정 I/O 작업이 일정 시간 내에 완료되지 않으면 발생하는 타임아웃. 
  - 클라이언트와 서버가 연결된 상태에서, 서버의 응답이 지연되거나 I/O 작업이 길어져 요청이 처리되지 않을 때 클라이언트는 연결을 끊는다. 
  - `Read Timeout`은 이러한 상황을 방지하기 위해 설정된 타임아웃으로, 일정 시간 내에 데이터가 읽혀지지 않으면 클라이언트가 연결을 종료한다.
- **네트워크에 타임아웃이 필요한 이유 &rarr; 타임아웃이 필요한 이유는 자원을 절약하기 위함**
  - 가령, 외부 서비스로 요청을 보냈지만 해당 요청이 무한정 길어질 수 있다. 이때 서비스의 요청이 자원을 가지고 있으면, 서비스의 자원이 고갈되어 장애가 발생할 수 있다. 타임아웃을 설정하면 이렇게 요청이 무한정 길어지는 상황을 예방할 수 있음.


### 사용자가 웹사이트에 처음 접근했을 때 발생하는 일련의 과정에 대해 설명
1. 사용자가 특정웹사이트에 접근하기 위해 브라우저에 도메인 입력
2. 입력된 도메인은 DNS서버를 통해서 서버의 IP획득
   - 로컬 DNS캐시, OS 캐시 확인후에 DNS서버 연결
3. 서버의 IP를 통해서 연결은 하기 위한 TCP 3Way Handshake를 통해 연결성립
   - HTTPS의 경우 TLS 핸드셰이크를 통해 암호화된 연결을 설정.
4. (일반적으로)80, 443번 포트를 통한 데이터 통신
5. 통신 완료후에 TCP 4way Handshake를 통해 연결 종료
   - 단, Keep-Alive 설정이 있으면 연결은 유지될 수 있음.
