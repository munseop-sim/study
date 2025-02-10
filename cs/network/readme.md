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