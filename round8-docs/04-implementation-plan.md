# Round 8 - 구현 플랜

> 최종 확정된 설계만 담은 문서. 의사결정 근거는 03-decisions.md 참고.

---

## 1. 전체 흐름 (Kafka-first 아키텍처)

```
[사전 인증]
  POST /auth/queue-token (DB 인증 1회)
  → 서명 기반 토큰 발급 (HMAC, Redis/DB 조회 불필요한 검증)
  → 클라이언트 토큰 저장

[대기열 진입]
  POST /queue/enter (서명 토큰으로 경량 인증)
  → Kafka produce (acks=all) → "접수 완료" 응답 + 초기 Jitter retryAfterMs
  → 아웃박스 불필요 (DB 상태와 원자적으로 맞출 대상 없음)

[Kafka Consumer]
  → Kafka에서 메시지 읽기 (순서 보장)
  → Redis INCR waiting-queue-seq → score 생성 (엄밀한 FIFO)
  → Redis ZADD waiting-queue {score} {userId}

[순번 조회 — Polling]
  GET /queue/position (서명 토큰으로 경량 인증)
  → ZRANK → position(rank+1) + 예상 대기 시간 + retryAfterMs 응답
  → etaToLiveZone ≤ 15초이면 SSE 연결 힌트 포함 (Nice-to-Have)
  → 갱신된 서명 토큰 재발급 (TTL 연장)

[Nice-to-Have: SSE 연결]
  → 클라이언트가 SSE 연결
  → 연결 즉시 snapshot 전송 (순번 + ETA + 토큰 여부)
  → 이후 ZPOPMIN마다 실시간 순번 Push (상위 100명)

[토큰 발급 스케줄러] → 100ms마다 실행 (ShedLock으로 단일 리더 보장)
  → Lua script: ZPOPMIN 18명 + SET entry-token + ZCARD cap 검사 (원자적)
  → SSE emitter 있으면 토큰 즉시 Push (Nice-to-Have)
  → ZRANGE 0 99 → 상위 100명 SSE Push (Nice-to-Have)

[주문]
  → 토큰 수신 (Polling 또는 SSE)
  → POST /orders (Header: X-Entry-Token)
  → 토큰 검증 → 주문 처리 → 토큰 삭제

[주문 이후] → Round 7 이벤트 파이프라인 동작
```

---

## 2. Must-Have 구현 항목

### 2-0. 인증 — 서명 기반 대기열 토큰

**POST /auth/queue-token — 대기열 전용 토큰 발급**

```
기존 DB 인증 (X-Loopers-LoginId/LoginPw → AuthenticationResolver)
→ userId 획득
→ token = userId + 만료시각 + HMAC(userId + 만료시각, secretKey)
→ 응답: { queueToken: "..." }
```

**대기열 API 인증 — QueueAuthPort**

```java
public interface QueueAuthPort {
    record ResolvedQueueToken(Long userId, String refreshedToken) {}
    ResolvedQueueToken resolveAndRefresh(String token);
    // 단일 호출: HMAC 검증 + userId 추출 + 갱신 토큰 생성. I/O 없음.
}
```

- DB 인증은 토큰 발급 시 1회만. 이후 대기열 API는 서명 검증만.
- Controller는 raw token을 Facade에 전달, 인증은 Service에서 resolveAndRefresh 1회 수행.
- 추후 JWT 이식 시 QueueAuthPort 구현체만 교체.
- Polling 응답 시 갱신된 토큰이 resolveAndRefresh 결과에 포함 (HMAC 생성 = CPU 연산, 수 μs).

### 2-1. Step 1 — Kafka-first 대기열

**POST /queue/enter — 대기열 진입**

```
// 서명 토큰 검증 + 갱신 (단일 호출, DB 안 침)
resolved = queueAuthPort.resolveAndRefresh(queueToken)

// Kafka produce (acks=all, 인증 성공 후에만 실행)
kafkaTemplate.send("waiting-queue-topic", resolved.userId().toString(), enterMessage)

// 진입 증명 발급 (stateless HMAC 서명 — Consumer 처리 지연 구간 식별용)
// → 결정 근거: 03-decisions.md #18
enterReceipt = queueAuthPort.generateEnterReceipt(resolved.userId())

// 응답 (순번은 Consumer 처리 후 Polling에서 확인)
응답:
{
  "status": "ACCEPTED",
  "retryAfterMs": random(0, 2000),  // 초기 Jitter
  "refreshedToken": "갱신된_서명토큰",
  "enterReceipt": "enter:{userId}:{timestamp}:{hmac}"  // Polling 시 X-Enter-Receipt 헤더로 전달
}
```

- NX 미사용: 재진입 시 Consumer가 ZADD → score 갱신 → 뒤로 밀림 (새로고침 억제)
- POST /queue/enter는 자동 재시도 금지 (LB/클라이언트 설정)
- Kafka produce 실패 시 유저에게 에러 → 재시도

**Kafka Consumer — Redis 적재**

```
// Kafka에서 순서대로 읽기
@KafkaListener(topics = "waiting-queue-topic")
void processEntry(ConsumerRecord record):
  userId = record.value().userId

  // INCR로 전역 단조 증가 순번 생성 (엄밀한 FIFO)
  score = INCR waiting-queue-seq

  // Redis에 적재 (순번 조회용)
  ZADD waiting-queue {score} {userId}

  // cap 검사는 ZADD 후 ZCARD로 확인, 초과 시 ZREM
  if (ZCARD waiting-queue > 1,000,000):
    ZREM waiting-queue {userId}
    // cap 초과 상태를 별도 저장 (유저가 Polling 시 안내)
```

- master-only RedisTemplate 사용 (REPLICA_PREFERRED는 stale read 위험)

**GET /queue/position — 순번 조회**

헤더: `X-Queue-Token` (필수), `X-Enter-Receipt` (optional — enter 직후 Consumer 처리 지연 구간 처리용)

```
// 서명 토큰 검증 + 갱신 (단일 호출)
resolved = queueAuthPort.resolveAndRefresh(queueToken)
userId = resolved.userId()

rank = ZRANK waiting-queue {userId}

// 상태 1: 대기 중
if (rank != null):
  position = rank + 1  // 1-based
  totalWaiting = ZCARD waiting-queue
  interval = getInterval(position)
  jitter = getJitter(position)
  retryAfterMs = interval + random(-jitter, +jitter)

  // SSE 연결 힌트 (Nice-to-Have)
  etaToLiveZone = ((position - 100) / 175.0) - (interval / 1000.0)
  sseConnectUrl = (etaToLiveZone <= 15) ? "/queue/stream" : null

  응답: { status: "WAITING", position, totalWaiting, estimatedWaitSeconds, retryAfterMs, sseConnectUrl, refreshedToken }

// 상태 2: 입장 토큰 발급 완료
if (rank == null):
  token = GET entry-token:{userId}
  if (token != null):
    응답: { status: "TOKEN_ISSUED", position: 0, entryToken: token, refreshedToken }

// 상태 3: cap 초과 거부
  if isRejected(userId):
    응답: QUEUE_CAPACITY_EXCEEDED 에러

// 상태 4: Consumer 처리 지연 (Kafka produce 성공 후 ZADD 전)
// → 결정 근거: 03-decisions.md #18
  if enterReceipt != null && queueAuthPort.resolveEnterReceipt(enterReceipt) == userId:
    응답: { status: "PROCESSING", retryAfterMs: 2000, refreshedToken }

// 상태 5: 미진입
  else:
    응답: QUEUE_NOT_ENTERED 에러
```

### 2-2. Step 2 — 입장 토큰 & 스케줄러

**토큰 발급 스케줄러 (100ms마다, ShedLock 단일 리더)**

```
@Scheduled(fixedRate = 100)
@SchedulerLock(name = "queue-token-issuer")  // ShedLock 분산 락
void processQueue():
  // Lua script로 원자적 처리 (ZPOPMIN + SET + ZCARD)
  users = executeLuaScript()

  // Nice-to-Have: SSE Push
  for (userId in users):
    emitter = emitters.get(userId)
    if (emitter != null):
      emitter.send(tokenEvent(token))
      emitters.remove(userId)

  // Nice-to-Have: 상위 100명 SSE Push
  top100 = ZRANGE waiting-queue 0 99
  for (userId in top100):
    emitter = emitters.get(userId)
    if (emitter != null):
      rank = indexOf(userId in top100)
      emitter.send(positionEvent(rank + 1))
```

**Lua script (원자적 토큰 발급)**

```lua
-- ZPOPMIN + SET EX를 원자적으로 처리
-- ARGV[1]=batchSize, ARGV[2]=ttl, ARGV[3..N]=Java에서 사전 생성한 entry-{UUID} 토큰
local users = redis.call('ZPOPMIN', KEYS[1], ARGV[1])  -- waiting-queue, 18
local results = {}
local idx = 0
for i = 1, #users, 2 do
  local userId = users[i]
  idx = idx + 1
  local token = ARGV[2 + idx]      -- entry-{UUID}
  redis.call('SET', 'entry-token:' .. userId, token, 'EX', ARGV[2])
  table.insert(results, userId)
end
return results
```

**토큰 검증 + order-lock (주문 API)**

동시 주문 방지를 위해 토큰 검증 후 Redis 락을 획득한다. → 결정 근거: 03-decisions.md #19

```
POST /orders 진입 시:
  tokenHeader = request.getHeader("X-Entry-Token")
  storedToken = GET entry-token:{userId}

  if (storedToken == null || !storedToken.equals(tokenHeader)):
    throw INVALID_QUEUE_TOKEN

  // 주문 처리 락 획득 (동시 주문 방지 — TOCTOU 차단)
  acquired = SET NX EX 300 "order-lock:{userId}"
  if (!acquired):
    throw ORDER_ALREADY_IN_PROGRESS

  try:
    // 주문 처리 ...

    // 주문 완료 후 정리 (best-effort)
    DEL entry-token:{userId}   // 성공 시 토큰 삭제
    DEL order-lock:{userId}    // 락 해제

  catch Exception:
    DEL order-lock:{userId}    // 실패 시 락만 해제 (토큰 보존 → 재시도 가능)
    throw
```

### 2-3. Step 3 — 실시간 순번 조회

**구간별 Polling 주기**

| 순번 구간 (position) | 방식 | 주기 (retryAfterMs) | Jitter |
|----------------------|------|---------------------|--------|
| 1~100 | SSE 실시간 (Nice-to-Have) / Polling 1초 (fallback) | 1000ms | 없음 |
| 101~1,000 | Polling | 2000ms | ±300ms |
| 1,001~10,000 | Polling | 5000ms | ±500ms |
| 10,001~100,000 | Polling | 10000ms | ±1000ms |
| 100,001+ | Polling | 30000ms | ±4000ms |

```java
// position 기준 (1-based)
int getInterval(long position) {
    if (position <= 100) return 1000;
    if (position <= 1000) return 2000;
    if (position <= 10000) return 5000;
    if (position <= 100000) return 10000;
    return 30000;
}

int getJitter(long position) {
    if (position <= 100) return 0;
    if (position <= 1000) return 300;
    if (position <= 10000) return 500;
    if (position <= 100000) return 1000;
    return 4000;
}
```

**예상 대기 시간**

```java
double estimatedWaitSeconds(long position) {
    return (double) position / 175;  // 보수적 TPS 기준
}
```

**Jitter 적용**

```java
// 초기 Jitter (POST /queue/enter 응답)
retryAfterMs = ThreadLocalRandom.current().nextInt(0, interval);

// 이후 Jitter (GET /queue/position 응답)
retryAfterMs = interval + ThreadLocalRandom.current().nextInt(-jitter, jitter + 1);
```

---

## 3. Nice-to-Have 구현 항목

### 3-1. SSE 기반 실시간 순번 Push (하이브리드)

**GET /queue/stream — SSE 연결**

```
SseEmitter emitter = new SseEmitter(timeout)
emitters.put(userId, emitter)

// 연결 즉시 snapshot 전송
rank = ZRANK waiting-queue {userId}
token = GET entry-token:{userId}
emitter.send(snapshot(rank + 1, estimatedWait, token))

// cleanup 등록
emitter.onCompletion(() -> emitters.remove(userId))
emitter.onTimeout(() -> emitters.remove(userId))
emitter.onError((e) -> emitters.remove(userId))

return emitter
```

**SSE 연결 힌트 — 동적 계산 (live zone 반영)**

```java
// GET /queue/position 응답 시
double etaToLiveZone = ((position - 100) / 175.0) - (getInterval(position) / 1000.0);
String sseConnectUrl = (etaToLiveZone <= 15) ? "/queue/stream" : null;
```

Polling 주기나 TPS를 변경해도 자동 적응.

### 3-2. Graceful Degradation (Kafka-first)

**전제**: Redis Sentinel/Cluster로 고가용성 확보.

**Redis 장애 시 fallback:**

```
정상: User → Kafka → Consumer → Redis ZADD → Polling ZRANK
장애: User → Kafka → 근사 순번 (Kafka offset 기반, 보수적 120명/초로 계산) → 서비스 유지
      토큰 발급: Consumer가 Kafka에서 읽어 직접 발급 (Redis 없이)
복구: Consumer가 밀린 Kafka 메시지 → Redis 재적재 → 정상 복귀
```

- 인증은 서명 기반이므로 Redis 장애 시에도 동작.
- 근사 순번: 실제보다 보수적으로 계산 → "예상보다 빨리 입장" = 좋은 경험.

---

## 4. Redis 명령어 정리

| 용도 | 명령어 | 비고 |
|------|--------|------|
| 순번 생성 | `INCR waiting-queue-seq` | 전역 단조 증가 (Consumer에서) |
| 대기열 적재 | `ZADD waiting-queue {score} {userId}` | NX 미사용, Consumer에서 |
| 순번 조회 | `ZRANK waiting-queue {userId}` | 0-based, position = rank + 1 |
| 전체 대기 인원 | `ZCARD waiting-queue` | O(1), position 응답에 totalWaiting으로 포함 |
| N명 꺼내기 + 토큰 발급 | Lua script (ZPOPMIN + SET EX) | 원자적, 스케줄러용 |
| 상위 100명 조회 | `ZRANGE waiting-queue 0 99` | SSE Push용 |
| 토큰 검증 | `GET entry-token:{userId}` | |
| 토큰 삭제 | `DEL entry-token:{userId}` | 주문 완료 후 |
| 주문 처리 락 획득 | `SET NX EX 300 "order-lock:{userId}"` | 동시 주문 방지 (#19) |
| 주문 처리 락 해제 | `DEL "order-lock:{userId}"` | 완료/실패 후 |
| cap 초과 거부 상태 | `SET "queue-rejected:{userId}" EX 60` | Consumer에서 저장, Polling 시 안내 |

**주의: 대기열 관련 Redis 연산은 반드시 master-only RedisTemplate 사용.** 현재 기본 RedisTemplate이 REPLICA_PREFERRED이므로, ZADD 후 ZRANK에서 stale read 가능.

---

## 5. 처리량 요약

| 항목 | 값 |
|------|-----|
| DB 커넥션 풀 | 50 |
| 주문 처리 시간 | 200ms |
| 이론적 최대 TPS | 250 |
| 안전 마진 | 70% |
| 목표 토큰 발급 TPS | 175 (실제 ~180) |
| 스케줄러 주기 | 100ms |
| 1회 배치 크기 | 18명 |
| 대기열 상한 | 1,000,000명 |
| 토큰 TTL | 300초 (5분) |
| 총 Polling 요청 (추산) | ~41,350 req/s |
| 스케줄러 리더 | ShedLock + Redis 분산 락 (단일 인스턴스) |

참고: 175 TPS는 토큰 발급(입장) 속도 제어이며, 주문 API 순간 TPS를 직접 보장하지는 않음.

---

## 6. 초기 burst 대응

100만명 동시 `POST /queue/enter` → 스케일아웃으로 버틴다. (멘토님 답변)

- Kafka-first: enter API는 Kafka produce만 수행 (DB 접근 없음, 가벼움)
- 인증: 서명 기반 토큰 (DB 조회 불필요)
- Kafka: burst 흡수에 강함 (높은 throughput)
- 서버 3~5대로 분산 처리

---

## 7. 주의사항

| 항목 | 내용 |
|------|------|
| NX 미사용 | POST /queue/enter만 뒤로 밀림. GET /queue/position은 영향 없음. 자동 재시도 금지 (LB/클라이언트 설정) |
| 토큰 발급 원자성 | Lua script 필수 (ZPOPMIN + SET). 비원자적이면 유저 영구 유실 가능 |
| Lua return 값 | `results`(userId만)를 반환해야 함. `users`(ZPOPMIN 원본) 반환 시 score가 혼합되어 캐스팅 예외 발생 |
| 스케줄러 단일 리더 | ShedLock + Redis 분산 락. 없으면 발급량 N배 증폭 |
| score 설계 | Redis INCR 기반 (timestamp 아님). FIFO 엄밀 보장 |
| master-only Redis | 대기열 연산은 반드시 master에서. REPLICA_PREFERRED는 stale read 위험 |
| 서명 기반 인증 | 대기열 API는 DB 인증 사용 금지. QueueAuthPort로 분리 |
| 토큰 성격 | TTL 내 재시도 가능한 입장권 (1회성 아님). userId + requestId 멱등성과 함께 동작 |
| enterReceipt | enter 응답에 포함. Polling 시 X-Enter-Receipt 헤더로 전달. Consumer 처리 지연 구간 PROCESSING 상태 반환용 (#18) |
| order-lock | 토큰 검증 직후 SET NX EX 획득. 동시 주문 1건만 허용. 실패 시 락만 해제(토큰 보존) (#19) |
| 멀티 인스턴스 SSE | sticky session 또는 Redis Pub/Sub 필요 |
| SSE 운영 | heartbeat, reconnect, emitter cleanup, LB timeout 설정 |
| 이탈 유저 | 즉시 정리 안 함. ZPOPMIN 후 토큰 TTL 만료로 자연 소멸. 이탈률 높으면 liveness + reaper 검토 |
