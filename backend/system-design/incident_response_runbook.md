# 장애 대응 런북 — Incident Response Runbook

> **관련 문서**
> - [관측성 스택](./observability_stack.md) — 로그·메트릭·트레이싱 파이프라인 구성
> - [관측성 개요](./observability.md) — SLI/SLO/SLA 개념, 알람 설계
> - [서킷 브레이커 패턴](./circuit_breaker.md) — 외부 의존 격리 전략
> - [배포 전략](./deployment.md) — 롤백, 블루-그린, 카나리 배포

---

## 1. 장애 대응 기본 원칙

### 1.1 즉시 대응(Mitigation) vs 근본 원인 분석(RCA) 분리

장애 발생 시 가장 흔한 실수는 **원인을 찾으려다 복구 타이밍을 놓치는 것**이다.
두 활동은 목적이 다르며, 반드시 순서를 지켜야 한다.

```
[장애 인지]
    ↓
[즉시 대응 — Service Restoration]   ← 모든 리소스 집중
    목표: 서비스를 최대한 빨리 복구
    수단: 롤백, 트래픽 절감, 서킷 브레이커, DB 프로세스 킬 등
    ↓ (서비스 안정화 확인)
[근본 원인 분석 — Root Cause Analysis]
    목표: 재발 방지
    수단: 5 Whys, 타임라인 재구성, 포렌식 데이터 분석
```

원인 분석 중에 서비스가 추가로 악화될 수 있다.
**복구 후 원인 분석**이 원칙이다.

### 1.2 커뮤니케이션 원칙

| 타이밍 | 액션 | 담당 |
|---|---|---|
| 장애 인지 즉시 | 내부 인시던트 채널 생성 + 발생 알림 | 최초 인지자 |
| **5분 이내** | Status Page 업데이트 ("조사 중") | 온콜 담당자 |
| 15분마다 | 진행 상황 업데이트 (채널 + Status Page) | 인시던트 리더 |
| 복구 완료 | 복구 완료 공지 + 임시 원인 공유 | 인시던트 리더 |
| 복구 후 24~72시간 | Post-Mortem 초안 공유 | 온콜 담당자 |

### 1.3 실시간 기록 원칙

- 모든 액션을 **타임스탬프와 함께** 인시던트 채널 또는 공유 문서에 기록한다.
- 나중에 Post-Mortem을 작성할 때 이 기록이 타임라인의 근거가 된다.
- 기록 예시:
  ```
  14:02 [알람] p99 레이턴시 > 3000ms (임계값: 500ms)
  14:04 [확인] order-service 에러율은 정상. 레이턴시만 급증.
  14:07 [조치] DB 슬로우 쿼리 로그 확인 시작
  14:12 [발견] payments 테이블 풀 테이블 스캔 쿼리 발견 (누락된 인덱스)
  14:15 [조치] 해당 쿼리 KILL + 인덱스 긴급 추가
  14:18 [확인] p99 레이턴시 정상화 (420ms)
  ```

---

## 2. 관측성 스택 활용 순서

장애 상황에서 어떤 도구를 먼저 확인해야 하는지 순서가 중요하다.

### 2.1 확인 순서

```
1단계: 로그 (즉각적 확인)
    → 에러 메시지, 스택 트레이스, 요청 ID
    → "무슨 일이 일어났는가?" 파악

2단계: 메트릭 (추세 파악)
    → CPU·메모리·JVM heap·커넥션 풀·요청 처리량·에러율
    → "언제부터, 어느 정도 규모로?" 파악

3단계: 분산 트레이싱 (서비스 특정)
    → Jaeger / Zipkin — 어느 서비스, 어느 span에서 지연이 발생하는지
    → "어디가 문제인가?" 정확히 특정
```

### 2.2 로그에서 확인할 것

```
# 에러 로그 집계 (최근 5분)
grep "ERROR" app.log | tail -200

# 특정 요청 ID 추적
grep "requestId=abc-123" app.log

# 스택 트레이스 패턴 집계
grep "Exception" app.log | grep -oP '^\S+Exception' | sort | uniq -c | sort -rn
```

### 2.3 메트릭에서 확인할 것

| 메트릭 | 임계값 기준 예시 | 의미 |
|---|---|---|
| p99 레이턴시 | 정상 대비 3배 이상 | 처리 병목 발생 |
| 에러율 | > 1% | 실패 급증 |
| JVM heap used | > 80% | GC 압박 |
| HikariCP pending | > 0 지속 | 커넥션 풀 포화 |
| CPU | > 85% 지속 | 연산 병목 |
| Kafka consumer lag | 급격히 증가 | 소비자 처리 지연 |

### 2.4 분산 트레이싱 활용

```
전체 요청 트레이스:
[API Gateway] → [order-service] → [payment-service] → [DB]
   5ms              8ms              2,800ms           3ms

→ payment-service 내부에서 2,800ms 소요 확인
→ payment-service의 DB span 드릴다운 → 특정 쿼리 750ms 반복
```

---

## 3. p99 레이턴시 급증 시 가설 우선순위 체크리스트

에러율 변화 없이 레이턴시만 급증하는 케이스에 적용한다.

```
□ 1. DB 슬로우 쿼리
    - SHOW PROCESSLIST;
    - SHOW STATUS LIKE 'Slow_queries';
    - 슬로우 쿼리 로그 확인 (long_query_time 초과)
    - 인덱스 미활용 쿼리, 풀 테이블 스캔
    - EXPLAIN으로 실행 계획 확인

□ 2. DB 커넥션 풀 포화
    - HikariCP metrics: hikari.connections.active / hikari.connections.pending
    - pool size vs 동시 요청 수 비교
    - 커넥션 리크 여부 (active 수가 max에 근접하고 pending > 0)

□ 3. 외부 의존 서비스 지연 (PG API, 환율 API 등)
    - 분산 트레이싱에서 downstream span 지연 확인
    - 외부 API timeout 설정값과 실제 응답 시간 비교
    - 해당 외부 서비스의 Status Page 확인

□ 4. GC 일시 정지 (Stop-the-World)
    - JVM GC 로그 확인: Full GC 빈도, pause time
    - jstat -gcutil <pid> 1000 10
    - 힙 사용량 급증 → GC 빈도 증가 패턴 확인

□ 5. 네트워크 레이턴시
    - ping, traceroute, 네트워크 인터페이스 에러 카운트
    - 동일 AZ/리전 내 지연인지 크로스 AZ인지 확인
    - 클라우드 공급자 Status Page 확인

□ 6. 최근 배포 / 코드 변경
    - 배포 타임라인 vs 레이턴시 급증 시점 상관관계
    - git log --oneline -10
    - 기능 플래그(Feature Flag) 변경 이력 확인

□ 7. 쓰레드 풀 포화
    - Thread dump: jstack <pid> > thread_dump.txt
    - BLOCKED / WAITING 쓰레드 비율 분석
    - 특정 락을 기다리는 쓰레드 체인 확인

□ 8. 캐시 미스율 급증
    - Redis / Caffeine hit rate 메트릭
    - 캐시 eviction 급증, TTL 만료 동시 다발 여부
    - 캐시 워밍업 미실행 후 배포 직후 트래픽 급증 패턴
```

---

## 4. 포렌식 명령 모음

### 4.1 JVM

```bash
# 쓰레드 덤프 (BLOCKED/WAITING 쓰레드 확인)
jstack <pid> > thread_dump.txt
grep -A 5 "BLOCKED" thread_dump.txt

# 힙 히스토그램 (메모리 점유 상위 클래스 확인)
jmap -histo <pid> | head -30

# 즉시 GC 실행 (테스트/임시 대응용, 운영 주의)
jcmd <pid> GC.run

# GC 통계 10회 (1초 간격)
jstat -gcutil <pid> 1000 10
# 출력: S0 S1 E O M CCS YGC YGCT FGC FGCT GCT
# FGC: Full GC 횟수, FGCT: 누적 Full GC 시간(초)
```

### 4.2 MySQL / PostgreSQL

```sql
-- 현재 실행 중인 쿼리 목록
SHOW PROCESSLIST;                                          -- MySQL
SELECT * FROM pg_stat_activity WHERE state = 'active';    -- PostgreSQL

-- InnoDB 락 대기 및 데드락 정보 (MySQL)
SHOW ENGINE INNODB STATUS;

-- 활성 트랜잭션 목록 (MySQL)
SELECT * FROM information_schema.innodb_trx;

-- 락 대기 관계 (PostgreSQL)
SELECT
    blocked.pid AS blocked_pid,
    blocking.pid AS blocking_pid,
    blocked.query AS blocked_query
FROM pg_stat_activity blocked
JOIN pg_stat_activity blocking ON blocking.pid = ANY(pg_blocking_pids(blocked.pid))
WHERE cardinality(pg_blocking_pids(blocked.pid)) > 0;

-- 쿼리 실행 계획
EXPLAIN ANALYZE [쿼리];

-- 프로세스 강제 종료
KILL <process_id>;                -- MySQL
SELECT pg_terminate_backend(pid); -- PostgreSQL
```

### 4.3 Redis

```bash
# 최근 슬로우 명령 상위 10개
redis-cli SLOWLOG GET 10

# 메모리 사용량 상세
redis-cli INFO memory

# 커맨드 통계 (hit/miss 포함)
redis-cli INFO stats

# 실시간 명령 모니터링 (주의: 부하 발생, 짧게만 사용)
redis-cli MONITOR

# 키스페이스 히트/미스 비율
redis-cli INFO keyspace
```

### 4.4 Kafka

```bash
# 컨슈머 그룹 lag 확인
kafka-consumer-groups.sh \
  --bootstrap-server <broker>:9092 \
  --describe \
  --group <consumer-group-id>

# 토픽 상세 정보 (파티션, 리더, 레플리카)
kafka-topics.sh \
  --bootstrap-server <broker>:9092 \
  --describe \
  --topic <topic-name>

# 특정 토픽 오프셋 확인
kafka-run-class.sh kafka.tools.GetOffsetShell \
  --broker-list <broker>:9092 \
  --topic <topic-name>
```

### 4.5 OS / 네트워크

```bash
# 쓰레드별 CPU 사용률 (JVM 쓰레드 특정)
top -H -p <pid>

# 활성 TCP 커넥션 수
netstat -antp | grep ESTABLISHED | wc -l

# 소켓 상태 요약 (ss가 netstat보다 빠름)
ss -tnp
ss -s    # 소켓 통계 요약

# 파일 디스크립터 사용량 (FD 부족 확인)
ls -la /proc/<pid>/fd | wc -l
cat /proc/sys/fs/file-max

# 시스템 메모리 현황
free -h
vmstat 1 10    # 1초 간격 10회 — si/so(스왑 I/O) 확인
```

---

## 5. 즉시 대응 vs 근본 원인 분석 의사결정 트리

### 5.1 즉시 대응 (Service Restoration) 옵션

```
서비스 중단 / 심각한 성능 저하 발생
          ↓
[배포 직후 발생했는가?]
    YES → 즉시 롤백 (이전 버전 배포)
    NO  ↓
[트래픽 급증이 원인인가?]
    YES → Rate Limiting 강화 / Feature Flag Off
    NO  ↓
[외부 의존 서비스 장애인가?]
    YES → Circuit Breaker 활성화 / Fallback 응답 반환
    NO  ↓
[DB 슬로우 쿼리 / 락 경합인가?]
    YES → 해당 프로세스 KILL + 긴급 인덱스 추가
    NO  ↓
[캐시 서버 장애인가?]
    YES → 캐시 우회(bypass) + 캐시 재시작 + 워밍업
    NO  ↓
[JVM 힙 고갈 / GC 과부하인가?]
    YES → 인스턴스 재시작 + 스케일 아웃
```

### 5.2 즉시 대응 수단 정리

| 수단 | 언제 사용 | 주의사항 |
|---|---|---|
| 이전 버전 롤백 | 배포 직후 레이턴시/에러율 급증 | 데이터 마이그레이션 포함 배포면 롤백 불가 |
| Rate Limiting 강화 | 트래픽 급증, 캐스케이딩 장애 방지 | 정상 사용자 영향 고려 |
| Feature Flag Off | 특정 기능 코드 의심 시 | 플래그 적용 범위 사전 파악 |
| Circuit Breaker 활성화 | 외부 API 지연으로 쓰레드 고갈 | Fallback 응답 품질 확인 |
| DB 프로세스 KILL | 슬로우 쿼리/락 장시간 점유 | 트랜잭션 롤백으로 데이터 정합성 확인 |
| 캐시 워밍업 | 캐시 콜드 스타트로 DB 과부하 | 워밍업 중 DB 부하 모니터링 |
| 인스턴스 재시작 | 메모리 누수, GC 수렴 불가 | 재시작 중 트래픽 처리 여부 확인 |

### 5.3 근본 원인 분석 (복구 후)

서비스가 안정화된 이후 수행한다.

```
1. 5 Whys 분석
   Why 1: p99 레이턴시가 급증했다
   Why 2: DB 커넥션 풀이 포화되었다
   Why 3: 특정 쿼리의 실행 시간이 급격히 증가했다
   Why 4: 테이블에 인덱스가 없어 풀 스캔이 발생했다
   Why 5: 코드 리뷰 시 쿼리 실행 계획 검증이 누락되었다
   → 재발 방지: PR 체크리스트에 EXPLAIN 확인 항목 추가

2. 타임라인 재구성
   - 실시간 기록 데이터 + 로그/메트릭 타임스탬프 대조
   - 최초 이상 징후 시각, 알람 발생 시각, 인지 시각, 복구 시각 정리

3. 개선 액션 아이템 도출
   - 각 Why에 대응하는 구체적 개선 사항
   - 담당자 지정 + Due Date 설정
   - 완료 여부 추적
```

---

## 6. SLO 위반 시 에스컬레이션

### 6.1 Error Budget 소진율 계산

```
Error Budget = 1 - SLO 목표치
예: 99.9% SLO → Error Budget = 0.1% (월간 약 43.8분)

소진율(Burn Rate) = 실제 에러율 / Error Budget 비율
예: 에러율 1%  →  Burn Rate = 1% / 0.1% = 10배 속도로 소진 중
    → 현재 추세라면 월간 budget을 4.38분 만에 소진
```

### 6.2 인시던트 등급 분류

| 등급 | 기준 | 에스컬레이션 | 목표 복구 시간(RTO) |
|---|---|---|---|
| **P0** | 전체 서비스 다운 또는 핵심 기능 완전 불가 | 즉시 — CTO, 전 팀 호출 | 15분 이내 |
| **P1** | 핵심 기능 부분 장애 (에러율 > 5%) | 15분 이내 — 온콜 엔지니어 + TL | 1시간 이내 |
| **P2** | 성능 저하 (p99 > SLO 임계값), 비핵심 기능 장애 | 1시간 이내 — 온콜 엔지니어 | 4시간 이내 |
| **P3** | 경미한 이상, Error Budget 소진 없음 | 다음 근무일 | 24시간 이내 |

### 6.3 Oncall Rotation 호출 기준

```
알람 발생
    ↓
온콜 엔지니어 자동 호출 (PagerDuty / OpsGenie)
    ↓ 15분 무응답
1차 에스컬레이션: 온콜 TL 호출
    ↓ 15분 추가 무응답
2차 에스컬레이션: 온콜 매니저 호출
    ↓ P0/P1 지속 시
3차 에스컬레이션: 경영진 보고 채널 알림
```

**Error Budget 소진율 기반 자동 알람**

```yaml
# Prometheus Alert 예시
- alert: ErrorBudgetBurnRateCritical
  expr: |
    (sum(rate(http_requests_total{status=~"5.."}[1h])) /
     sum(rate(http_requests_total[1h]))) > 0.001 * 14.4
  # 1시간 burn rate 14.4배 = 월간 budget을 약 50시간 만에 소진하는 fast burn 기준
  for: 2m
  labels:
    severity: critical
  annotations:
    summary: "Error budget 소진율 위험 수준"
```

---

## 7. Post-Mortem 템플릿

Post-Mortem은 비난 없는(Blameless) 문화를 기반으로 작성한다.
목적은 **시스템과 프로세스 개선**이며, 개인의 실수를 지적하는 자리가 아니다.

### 7.1 장애 요약

```markdown
## 장애 요약

- **서비스**: [영향받은 서비스명]
- **영향 범위**: [사용자 수 / 트랜잭션 수 / 금전적 영향]
- **지속 시간**: [시작 시각] ~ [종료 시각] (총 X분)
- **심각도**: P0 / P1 / P2
- **최초 감지**: 알람 자동 감지 / 사용자 제보 / 내부 모니터링
```

### 7.2 타임라인

```markdown
## 타임라인

| 시각 | 이벤트 | 담당 |
|---|---|---|
| HH:MM | 최초 이상 징후 발생 (알람 또는 로그) | 시스템 |
| HH:MM | 온콜 엔지니어 인지 | - |
| HH:MM | Status Page 업데이트 ("조사 중") | - |
| HH:MM | [가설 1] 확인 및 기각 | - |
| HH:MM | 근본 원인 특정 | - |
| HH:MM | 대응 조치 실행 | - |
| HH:MM | 서비스 복구 확인 | - |
| HH:MM | Status Page 업데이트 ("복구 완료") | - |
```

### 7.3 근본 원인

```markdown
## 근본 원인

### 직접 원인
[즉각적으로 장애를 유발한 원인]
예: payments 테이블 조회 쿼리에 인덱스 누락으로 풀 테이블 스캔 발생

### 기여 원인 (5 Whys)
1. Why: 쿼리가 풀 테이블 스캔을 했다
2. Why: user_id 컬럼에 인덱스가 없었다
3. Why: 스키마 변경 PR에서 인덱스 추가가 누락되었다
4. Why: PR 체크리스트에 인덱스 검증 항목이 없었다
5. Why: 인덱스 리뷰 프로세스가 팀 내 문서화되지 않았다
```

### 7.4 즉시 대응 조치

```markdown
## 즉시 대응 조치

| 시각 | 조치 내용 | 효과 |
|---|---|---|
| HH:MM | 슬로우 쿼리 프로세스 KILL | p99 일시 회복 |
| HH:MM | 인덱스 긴급 추가 (무중단) | p99 정상화 |
```

### 7.5 재발 방지 액션

```markdown
## 재발 방지 액션 아이템

| # | 액션 | 담당자 | Due Date | 상태 |
|---|---|---|---|---|
| 1 | PR 체크리스트에 EXPLAIN 확인 항목 추가 | - | YYYY-MM-DD | TODO |
| 2 | 슬로우 쿼리 알람 임계값 하향 (5s → 1s) | - | YYYY-MM-DD | TODO |
| 3 | 인덱스 누락 자동 감지 린트 도입 검토 | - | YYYY-MM-DD | TODO |
| 4 | 장애 발생 서비스 부하 테스트 재실행 | - | YYYY-MM-DD | TODO |
```

### 7.6 면접에서 Post-Mortem 경험 연결 포인트

- 구체적인 수치로 말하기: "p99 레이턴시가 450ms에서 3,200ms로 급증했고, 7분 만에 복구했습니다."
- 본인이 한 행동 중심: "저는 분산 트레이싱으로 payment-service의 특정 span을 특정했고, EXPLAIN으로 인덱스 누락을 확인했습니다."
- 기술적 사실과 의사결정 논리 병기: "롤백 대신 인덱스 추가를 선택한 이유는 배포 직후가 아니었고, 롤백 시 스키마 불일치 위험이 있었기 때문입니다."
- 재발 방지로 마무리: "이후 PR 체크리스트에 EXPLAIN 확인 항목을 추가하여 같은 문제가 재발하지 않도록 했습니다."

---

## 8. 요약 비교

| 항목 | 즉시 대응 (Mitigation) | 근본 원인 분석 (RCA) |
|---|---|---|
| 목표 | 서비스 복구 | 재발 방지 |
| 타이밍 | 장애 발생 즉시 | 서비스 복구 후 |
| 주요 수단 | 롤백, CB, 프로세스 킬 | 5 Whys, 타임라인 재구성 |
| 소요 시간 | 분~시간 단위 | 시간~일 단위 |
| 산출물 | 복구 확인, 로그 기록 | Post-Mortem 문서, 액션 아이템 |

---

## 면접 포인트

### Q1. p99 레이턴시가 급증했는데 에러율은 정상일 때 첫 번째로 무엇을 확인하는가?

에러율 변화 없이 레이턴시만 급증한다는 것은 요청 자체는 성공하지만 처리 시간이 길어지는 병목이 생겼다는 신호다.

첫 번째로는 **분산 트레이싱**으로 어느 서비스의 어느 span에서 지연이 발생하는지 특정한다.
지연 지점을 좁힌 뒤, 해당 서비스의 **DB 슬로우 쿼리 로그**를 확인한다. 인덱스 미활용 쿼리나 풀 테이블 스캔이 가장 흔한 원인이다.

동시에 **HikariCP 커넥션 풀 메트릭**에서 `pending` 값이 0보다 크면 커넥션 풀 포화를 의심한다.
그 외로는 최근 배포 타임라인과 레이턴시 급증 시점의 상관관계, GC 로그의 Full GC 빈도 순서로 확인한다.

### Q2. DB 커넥션 풀 포화를 어떻게 확인하고 즉시 해소하는가?

**확인 방법**:
- HikariCP 메트릭: `hikari.connections.active`, `hikari.connections.pending`
- `pending > 0`이 지속되고 `active ≒ maximum-pool-size`이면 포화 상태
- `SHOW PROCESSLIST` (MySQL) 또는 `pg_stat_activity` (PostgreSQL)로 현재 실행 중인 쿼리 확인

**즉시 해소 방법**:
1. 장시간 점유 중인 슬로우 쿼리 프로세스 KILL (`KILL <pid>`)
2. 커넥션 리크가 의심되면 해당 인스턴스 재시작 (트래픽 절감 후)
3. 임시로 `maximum-pool-size` 증가 (DB 수용 가능 범위 내에서)
4. 요청 처리량 자체를 줄이기 위해 rate limiting 또는 feature flag 적용

**근본 원인 분석(복구 후)**:
- 슬로우 쿼리라면 인덱스 추가 또는 쿼리 튜닝
- 커넥션 리크라면 코드에서 커넥션 반환 누락 지점 식별

### Q3. 장애 복구와 근본 원인 분석을 왜 분리해야 하는가?

두 가지 이유가 있다.

첫째, **타임 프레셔(Time Pressure)**: 장애 중에는 매 분이 사용자 피해와 직결된다. 원인 분석에 시간을 쓰는 동안 피해가 누적된다. 롤백처럼 원인을 몰라도 즉시 적용할 수 있는 수단이 있다면 먼저 복구하는 것이 맞다.

둘째, **인지 부하(Cognitive Load)**: 장애 복구 중에는 스트레스가 높아 분석의 정확성이 떨어진다. 복구 후 안정된 상태에서 데이터를 차분히 분석해야 잘못된 원인 특정과 불필요한 변경을 막을 수 있다.

단, 분리가 **RCA를 미루거나 생략해도 된다는 의미가 아니다**. 복구 완료 후 반드시 Post-Mortem을 수행하고 재발 방지 액션을 도출해야 한다.

### Q4. Post-Mortem에서 가장 중요한 섹션은 무엇이라고 생각하는가?

**재발 방지 액션 아이템** 섹션이 가장 중요하다.

타임라인과 근본 원인은 "무슨 일이 있었는가"를 설명하지만, 액션 아이템이 없으면 Post-Mortem은 단순 사고 기록으로 끝난다. 실질적인 가치는 "다음에는 이 장애가 반복되지 않는다"는 보장에서 나온다.

좋은 액션 아이템의 조건:
- **구체적**: "모니터링 강화" 대신 "payments 서비스 p99 알람 임계값을 1,000ms로 설정"
- **담당자 지정**: 주인이 없는 액션은 실행되지 않는다
- **Due Date**: 기한 없는 계획은 계획이 아니다
- **추적 가능**: Jira 티켓 또는 체크리스트로 완료 여부를 추적

추가로, **Blameless 원칙**을 지키는 것도 중요하다. 개인을 지목하는 Post-Mortem은 다음 장애를 숨기게 만드는 조직 문화를 만든다.

### Q5. 슬로우 쿼리가 의심될 때 운영 DB에 EXPLAIN을 실행해도 되는가?

`EXPLAIN`은 실제 쿼리를 실행하지 않고 실행 계획만 반환하므로 부하가 없어 운영 중에도 안전하게 실행할 수 있다.
단, `EXPLAIN ANALYZE`는 실제 쿼리를 실행하므로 **무거운 쿼리에 운영 DB에서 직접 실행하면 위험하다**.
의심 쿼리에 `EXPLAIN ANALYZE`가 필요하다면 슬레이브(Read Replica)에서 실행하거나, 데이터 규모가 유사한 스테이징 환경을 활용하는 것이 원칙이다.
