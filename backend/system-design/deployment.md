# 배포 및 운영 전략

---

## Graceful Shutdown (우아한 종료)

> 출처: https://www.maeil-mail.kr/question/190

### 개념

**우아한 종료(Graceful Shutdown)** 란 애플리케이션이 종료될 때 바로 종료하는 것이 아니라, **현재 처리하고 있는 작업을 마무리하고 리소스를 정리한 이후 종료**하는 방식입니다.

서버 애플리케이션에서 일반적인 Graceful Shutdown은 SIGTERM 신호를 받았을 때:
1. 새로운 요청을 차단
2. 기존 처리 중인 요청을 모두 완료
3. 프로세스를 종료

### 필요성

즉각 종료 시 발생하는 문제:
- **트랜잭션 비정상 종료**
- **데이터 손실**
- **사용자 경험 저하**

### SIGTERM vs SIGKILL

| 신호 | 설명 | 처리 가능 여부 |
|---|---|---|
| **SIGTERM** | 소프트 종료 요청 신호 | 가능 — 종료 전 정리 작업 수행 후 종료 |
| **SIGKILL** | 강제 종료 신호 | 불가능 — 즉시 강제 종료 (정리 작업 없음) |

### Spring에서 Graceful Shutdown 설정

```properties
# application.properties
server.shutdown=graceful
spring.lifecycle.timeout-per-shutdown-phase=20s
```

- `server.shutdown=graceful`: Graceful Shutdown 활성화
- `timeout-per-shutdown-phase=20s`: 기존 작업 완료 대기 시간. 20초를 초과하면 강제 종료

주의사항: 기존 처리 중인 요청에서 **데드락이나 무한 루프**가 발생하면 타임아웃까지 프로세스가 종료되지 않을 수 있음.

### 참고 자료

- [JVM의 종료와 Graceful Shutdown](https://effectivesquid.tistory.com/entry/JVM%EC%9D%98-%EC%A2%85%EB%A3%8C%EC%99%80-Graceful-Shutdown)
- [SpringBoot Graceful-Shutdown 개념과 동작 원리](https://velog.io/@byeongju/SpringBoot%EC%9D%98-Graceful-Shutdown)
- [SIGKILL vs SIGTERM 리눅스 종료 신호](https://velog.io/@480/SIGKILL-vs-SIGTERM-%EB%A6%AC%EB%88%85%EC%8A%A4-%EC%A2%85%EB%A3%8C-%EC%8B%A0%ED%98%B8)

---

## Redis 분산 잠금 (Distributed Lock)

> 출처: https://www.maeil-mail.kr/question/194

### 개념

분산 환경에서 여러 서버가 동일한 자원에 동시에 접근하는 것을 제어하기 위해 **분산 잠금(Distributed Lock)** 을 사용합니다.

### Redis SET NX를 이용한 기본 구현

Redis의 `SET` 명령어에 `NX` 옵션을 추가하여 분산 잠금을 구현합니다.

- **NX 옵션**: 특정 Key에 해당하는 값이 존재하지 않는 경우에만 값 추가 작업 성공
- SET 작업을 성공적으로 수행한 서버가 잠금을 획득
- 작업 완료 후 Key를 제거하여 잠금 해제

```
서버A: SET lock_key "server_a" NX EX 30  → 성공 (잠금 획득)
서버B: SET lock_key "server_b" NX EX 30  → 실패 (이미 잠금 존재)
서버A: DEL lock_key                        → 잠금 해제
```

### 단일 Redis의 문제점: 잠금 유실

레플리케이션 구성에서 잠금이 유실될 수 있습니다:

1. 서버A가 마스터 노드에서 잠금 획득
2. 마스터 노드 장애 발생 → 레플리카 노드가 마스터로 승격(failover)
3. **복제 지연**으로 인해 새 마스터에 잠금 데이터 없음
4. 서버B도 잠금 획득 가능 → **상호 배제(Mutual Exclusion) 위반**

### RedLock 알고리즘

Redis 공식에서 제안한 **다중 노드 기반 분산 잠금** 알고리즘입니다.

**동작 방식:**
1. N개(홀수 권장, 예: 5개)의 **독립적인** Redis 노드에 순차적으로 잠금 획득 시도
2. **과반수(N/2 + 1) 이상의 노드에서 잠금 획득 성공** → 분산 잠금 획득으로 간주
3. 잠금 획득 실패 시 모든 노드에서 잠금 해제

```
5개 노드 중 3개 이상에서 SET NX 성공 → 잠금 획득
```

**특징:**
- 단일 마스터 장애에 의한 잠금 유실 방지
- 네트워크 지연, 시계 드리프트 등을 고려한 만료 시간 계산 필요

### 참고 자료

- [Redis - Distributed Locks with Redis](https://redis.io/docs/latest/develop/use/patterns/distributed-locks/)
- [망나니개발자 - 레디스가 제공하는 분산락(RedLock)의 특징과 한계](https://mangkyu.tistory.com/311)
- [컬리 기술 블로그 - 풀필먼트 입고 서비스팀에서 분산락을 사용하는 방법](https://helloworld.kurly.com/blog/distributed-redisson-lock/)
- [와디즈 기술 블로그 - 분산 환경 속에서 '따닥'을 외치다](https://blog.wadiz.kr/%EB%B6%84%EC%82%B0-%ED%99%98%EA%B2%BD-%EC%86%8D%EC%97%90%EC%84%9C-%EB%94%B0%EB%8B%A5%EC%9D%84-%EC%99%B8%EC%B9%98%EB%8B%A4/)
- [채널톡 기술 블로그 - Distributed Lock 구현 과정](https://channel.io/ko/blog/articles/abc2d95c)

---

## 무중단 배포 (Zero-Downtime Deployment)

> 출처: https://www.maeil-mail.kr/question/195

### 개념

**무중단 배포(Zero-Downtime Deployment)** 는 서비스에 다운 타임이 발생하지 않으면서 새로운 버전의 애플리케이션을 서버에 배포하는 것입니다.

### 배포 방식 비교

#### 롤링 배포 (Rolling Deployment)

서버를 **한 대씩 순차적으로 업데이트**하는 가장 기본적인 방식입니다.

```
[v1][v1][v1][v1]  →  [v2][v1][v1][v1]  →  [v2][v2][v1][v1]  →  [v2][v2][v2][v2]
```

**특징:**
- 새로운 서버를 별도로 생성하지 않음 → 비용 절약
- 배포 진행 중인 서버는 요청 처리 불가 → 다른 서버에 트래픽 집중 가능
- **하위 호환성(Backward Compatibility)** 고려 필수 (배포 중 두 버전 공존)

**적합한 상황:**
- 비용 제약이 있는 경우
- 버그 수정 후 소수 서버에만 먼저 배포하여 검증하고 싶을 때 (롤링의 부분 배포 활용)

#### 블루/그린 배포 (Blue/Green Deployment)

기존 서버(Blue)와 동일한 스펙의 신규 서버(Green)를 **미리 준비**하고, 신규 버전 배포 후 트래픽을 한 번에 전환하는 방식입니다.

```
[Blue: v1 서버들] → Green에 v2 배포 → 트래픽 전환 → [Green: v2 서버들]
```

**특징:**
- **즉각적인 롤백 가능** (Blue 서버 유지 상태에서 트래픽만 되돌리면 됨)
- 두 세트의 서버를 준비해야 하므로 **비용 증가**
- 배포 중 버전 공존 없음 → 호환성 문제 상대적으로 적음

**적합한 상황:**
- 대규모 업데이트, 전면적인 기술 부채 해결 등 중요한 변화가 있을 때

#### 카나리 배포 (Canary Deployment)

기존 버전과 새 버전을 동시에 운영하며, **트래픽의 일부(%)를 점진적으로 신규 버전으로 이동**시키는 방식입니다.

```
[v1: 100%] → [v1: 70%, v2: 30%] → [v1: 30%, v2: 70%] → [v2: 100%]
```

**특징:**
- 실제 트래픽으로 새 버전을 점진적으로 검증
- **하위 호환성** 고려 필수 (두 버전 공존)
- A/B 테스트에 활용 가능

**적합한 상황:**
- 오류율, 성능 등을 통계적으로 확인하고 싶을 때
- A/B 테스트를 진행할 때

### 배포 방식 비교표

| 방식 | 비용 | 롤백 속도 | 호환성 이슈 | 다운타임 |
|---|---|---|---|---|
| 롤링 | 낮음 | 느림 | 있음 | 없음 |
| 블루/그린 | 높음 | 빠름 | 낮음 | 없음 |
| 카나리 | 중간 | 중간 | 있음 | 없음 |

### 참고 자료

- [무중단 배포 아키텍처와 배포 전략 (Rolling, Blue/Green, Canary)](https://hudi.blog/zero-downtime-deployment/)
- [테코블 - 무중단 배포](https://tecoble.techcourse.co.kr/post/2022-11-01-blue-green-deployment/)

---

## IaC (Infrastructure as Code)

> 출처: https://www.maeil-mail.kr/question/219

### 개념

**코드형 인프라(Infrastructure as Code, IaC)** 는 수동 프로세스 대신 **코드를 통해 인프라를 프로비저닝하고 관리**하는 방법입니다.

기존 수동 설정 방식의 문제:
- 반복 작업이 많고 **휴먼 에러** 발생 가능
- 인프라 설정을 별도로 문서화해야 하는 번거로움
- 환경 간 불일치(개발/스테이징/프로덕션)

### 방식

#### 선언적(Declarative) 방식

**최종 상태**를 정의하면 IaC 도구가 자동으로 구성하는 방식. 사용자는 원하는 결과만 기술하면 됨.

- 대표 도구: **Terraform**, AWS CloudFormation

```hcl
# Terraform 예시 - 원하는 상태만 선언
resource "aws_instance" "web" {
  ami           = "ami-0c55b159cbfafe1f0"
  instance_type = "t2.micro"
}
```

#### 명령형(Imperative) 방식

**구성 방법**을 직접 정의하는 방식. 사용자가 인프라를 설정하는 단계를 코드로 정의.

- 대표 도구: **Ansible**, AWS CDK

```yaml
# Ansible 예시 - 실행 단계를 명령으로 정의
- name: Install nginx
  apt:
    name: nginx
    state: present
```

### 장단점

**장점:**
- Git 등 형상 관리 도구로 변경 사항 추적 가능
- 코드 자체가 문서 역할 → 코드 리뷰를 통한 인프라 변경 검토
- 코드 실행만으로 인프라 구축 자동화
- 코드 재사용으로 비슷한 인프라를 빠르게 구축 가능

**단점:**
- 다양한 도구 사용법 학습 필요 → 러닝 커브
- 인프라 상태 관리가 복잡할 수 있음
- 인프라 변경 시 문제 발생 시 디버깅이 어려울 수 있음

### 참고 자료

- [마켓컬리 - DevOps팀의 Terraform 모험](https://helloworld.kurly.com/blog/terraform-adventure/)

---

## 서버리스 (Serverless)

> 출처: https://www.maeil-mail.kr/question/248

### 개념

**서버리스(Serverless)** 란 클라우드 업체에서 직접 인프라를 관리하고 동적으로 크기를 조정하면서 유지 관리하는 방식입니다. AWS EC2처럼 서버를 직접 운영할 때 필요한 OS 관리, 보안, 파일 시스템 관리 등을 클라우드 업체가 모두 담당합니다.

**이점:**
- 직접 컴퓨터를 관리할 필요 없음
- 유연한 자동 확장(Auto Scaling)
- **사용하지 않은 용량에 대해서는 비용을 지불하지 않음** (사용량 기반 과금)

**서버리스 예시:** AWS Lambda(컴퓨팅), S3(스토리지), SQS(메시지 큐)

### 서버리스 아키텍처 백엔드 개발 방식

#### FaaS (Function as a Service)

특정 이벤트가 발생했을 때만 함수가 실행되는 방식입니다.

```
이벤트 발생 (GET /hello 요청)
    → 함수 실행 (Lambda 함수 호출)
    → 처리 결과 응답
    → 함수 종료
```

개발자는 비즈니스 로직을 작성하고, 클라우드 인프라에 코드 조각(함수)을 배포하면 됩니다.

**Cold Start 문제:** 함수가 오랫동안 실행되지 않으면 컨테이너가 종료되고, 다음 요청 시 새로 시작하는 지연 발생.

#### BaaS (Backend as a Service)

클라우드 제공업체에서 만든 **완성된 백엔드 기능**을 사용하는 방식입니다.

- **Firebase Authentication**: 로그인 기능을 비교적 적은 코드로 구현 가능
- **AWS Cognito**: 사용자 인증/인가 서비스
- **Firebase Firestore**: 실시간 데이터베이스

### 장단점

**장점:**
- 인프라 관리 불필요 → 개발자가 비즈니스 로직에 집중
- 자동 확장으로 트래픽 급증 대응 용이
- 사용량 기반 과금으로 비용 효율적

**단점:**
- **Cold Start**: 초기 응답 지연 발생 가능
- 실행 시간 제한 (AWS Lambda: 최대 15분)
- 벤더 의존성(Vendor Lock-in) 위험
- 로컬 상태 유지 불가 (무상태 설계 필요)
- 디버깅 및 테스트가 상대적으로 어려움

### 참고 자료

- [요즘 IT - 진짜 서버리스 vs 가짜 서버리스](https://yozm.wishket.com/magazine/detail/2168/)
- [AWS - 서버리스 컴퓨팅이란 무엇인가요?](https://aws.amazon.com/ko/what-is/serverless-computing/)

---

## 헬스체크 (Health Check)

> 출처: https://www.maeil-mail.kr/question/292

### 개념

**헬스체크(Health Check)** 는 시스템이 정상적으로 동작하고 있는지 확인하는 메커니즘입니다. 로드 밸런서, 컨테이너 오케스트레이터(Kubernetes 등)에서 서비스의 가용성을 판단하고 트래픽을 제어하는 데 사용됩니다.

### 종류

#### Liveness Probe (생존 확인)

애플리케이션이 **살아있는지(running)** 확인합니다. 실패 시 컨테이너를 재시작합니다.

- 데드락, 무한 루프 등으로 응답 불가 상태 감지
- 예: `/actuator/health/liveness`

#### Readiness Probe (준비 확인)

애플리케이션이 **요청을 처리할 준비가 됐는지** 확인합니다. 실패 시 로드 밸런서에서 트래픽을 제거합니다.

- DB 연결, 캐시 연결 등 의존성 확인
- 예: `/actuator/health/readiness`

#### Startup Probe (시작 확인)

애플리케이션이 **시작됐는지** 확인합니다. 느리게 시작하는 애플리케이션에 유용합니다.

### Spring Boot Actuator 헬스체크

Spring Boot Actuator를 사용하면 자동으로 헬스체크 엔드포인트가 제공됩니다.

```yaml
# application.yml
management:
  endpoint:
    health:
      show-details: always
      probes:
        enabled: true
  endpoints:
    web:
      exposure:
        include: health
```

기본 헬스 인디케이터:
- DB 연결 상태
- 디스크 용량
- Redis 연결 상태
- Kafka 연결 상태

### Kubernetes 헬스체크 설정 예시

```yaml
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  initialDelaySeconds: 30
  periodSeconds: 10

readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080
  initialDelaySeconds: 10
  periodSeconds: 5
```
