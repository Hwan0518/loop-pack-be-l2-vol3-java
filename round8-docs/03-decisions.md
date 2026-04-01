# Round 8 - 의사결정 기록

> 대기열 시스템 설계 과정에서의 모든 의사결정, 선택지 비교, trade-off, 기각 이유를 기록한 문서.
> "왜 이렇게 결정했는가"를 추적할 수 있도록 판단 근거를 남긴다.

---

## 1. Back-pressure 전략 — Queuing 메인 + Rate Limiting 보조

**결론**: 정상 유저는 대기열(Queuing)에 넣어 순서대로 처리, 대기열 상한(100만명) 초과 시 Rate Limiting으로 거부.

- 근거: 블랙 프라이데이 시나리오에서 유저는 기다릴 의사가 있음. "나중에 다시 시도하세요"(Rate Limiting)보다 "512번째입니다"(Queuing)가 이탈률을 낮춤.
- 대기열 cap 100만명: Redis 메모리 보호 + 100만번째 유저의 예상 대기 시간이 이미 95분이므로 현실적 상한.
- 양자택일이 아님: 봇/비정상 → Rate Limiting으로 사전 차단, 정상 유저 → 대기열 진입.

---

## 2. ZADD NX 미사용 — 새로고침 시 뒤로 밀림

**선택지:**

| 방식 | 동작 | 새로고침 억제 | 유령 대기자 |
|------|------|:---:|:---:|
| NX 사용 | 이미 있으면 무시 | ❌ | 토큰 TTL로 소멸 |
| NX 미사용 + 서버 방어 | ZRANK 확인 후 기존 순번 반환 | ❌ (NX와 동일) | - |
| **NX 미사용 + 서버 방어 없음** | 무조건 ZADD → score 갱신 → 뒤로 밀림 | **✅** | 자연 소멸 |

**결론**: NX 미사용 + 서버 방어 없음.

- **핵심 근거 — 새로고침 억제 (매우 강력한 트래픽 감소 효과)**: "새로고침하면 뒤로 밀린다"는 사실이 유저의 불필요한 요청을 자제시킴. 100만 유저가 평균 5번 새로고침하면 500만 enter 요청이지만, 억제 시 100만 건으로 줄어듦. 대기열 시스템에서 **가장 비용 없이 강력한 트래픽 방어 수단**.
- 유령 대기자 참고: NX 미사용이 유령 대기자를 자연 소멸시키는 것은 아님. 이탈한 유저는 ZPOPMIN될 때까지 큐에 남아있음. 이탈 유저는 토큰 TTL 만료로 자연 정리됨.
- trade-off: 네트워크 끊김, 브라우저 재전송, LB 자동 retry 등 의도치 않은 재요청에도 뒤로 밀림. 감수.
- 전제: `POST /queue/enter`는 자동 재시도 금지 (LB/클라이언트 설정). `GET /queue/position`(Polling)은 ZADD를 호출하지 않으므로 순번에 영향 없음.

---

## 3. 입장 토큰 설계 — "entry-" prefix + UUID, TTL 5분

**결론**: `SET entry-token:{userId} entry-{UUID} EX 300`

- TTL 5분: 결제 정보 입력에 충분한 시간. 너무 짧으면(1분) 결제 중 만료, 너무 길면(10분) 이탈자가 오래 자리 차지.
- prefix `entry-`: 디버깅/로그에서 토큰 종류 식별 용도. 비용 0.
- 토큰 성격: TTL(5분) 동안 재시도 가능한 입장권. 주문 완료 시 삭제. 기존 주문 도메인의 `userId + requestId` 멱등성과 함께 동작.

---

## 4. 처리량 산정 — TPS 175, 100ms × 18명

**결론**: 안전 마진 70% 적용, 100ms 주기로 18명씩 토큰 발급.

```
DB 커넥션 50 / 처리시간 0.2s = 250 TPS (이론적 최대)
250 × 0.7 = 175 TPS (안전 마진 적용)
175 / 10 = 17.5 ≈ 18명 (100ms당 배치 크기)
```

- 안전 마진 70% 이유: DB 커넥션을 주문만 쓰는 게 아님, 처리 시간 변동, 피크 시 지연 가능.
- 70%는 기본값이며, 모니터링 후 조절.
- 100ms 주기: Thundering Herd 완화 (1초에 175명 한 번에 → 100ms에 18명씩).

---

## 5. Thundering Herd 완화 — 발급 간격 분산만 적용

**선택지:**

| 전략 | 적용 | 판단 근거 |
|------|:---:|----------|
| **발급 간격 분산** | **✅** | 100ms × 18명, 구현 단순, 부하 10배 평탄화 |
| 토큰 Jitter | ❌ | 유저가 토큰 수신 즉시 주문 시도 → 비활성 토큰으로 실패 → 새로고침 → 대기열 뒤로 밀림 (NX 미사용). UX 붕괴. |
| 주문 API Rate Limit | ❌ | 10분+ 대기 후 429 거부 → 토큰 TTL 내 재시도 실패 시 토큰 만료 → 대기열 재진입. UX 붕괴. |

**핵심 원칙**: "정상 부하 상황에서, 대기열을 통과한 유저는 주문 API에서 실패하면 안 된다." (인프라 장애는 별도 정책)
- 토큰 Jitter와 API Rate Limit은 일반적으로 나쁜 기법이 아니라, 현재의 Polling 계약과 NX 미사용 재진입 정책에서 부적합한 것.

- 18명 동시 호출은 DB 커넥션 50개 기준으로 충분히 처리 가능.
- SSE Push에서의 Jitter는 서버→클라이언트 단방향이라 UX 영향 없으므로 별도 적용. 토큰 Jitter와는 다른 맥락.

---

## 6. 순번 피드백 방식 — Polling 메인 (SSE는 Nice-to-Have)

**의사결정 흐름 (4단계 변천):**

1차: "SSE 메인 + Polling fallback"
→ 2차: "하이브리드 (이벤트 기반 토큰 + 스케줄러 기반 순번)"
→ 3차: "순번은 상태(state)라 SSE로 보내도 주기적 갱신 → Polling과 차이 없음" 발견
→ 4차 최종: "Polling 메인. SSE는 Nice-to-Have로 입장 임박 구간에만 적용"

**결론**: Polling 기반 순번 조회가 Must-Have. SSE는 Nice-to-Have.

- 근거: "순번"은 이벤트가 아니라 상태. 100ms마다 ZPOPMIN으로 전원 순번 변동. 전체에 SSE Push 불가능. 스케줄러로 주기적 Push하면 "서버 측 Polling"과 동일.
- SSE의 실질적 이점: 상위 100명(SSE 구간)에 토큰 즉시 전달. SSE가 없으면 해당 구간은 1초 Polling fallback이므로 최대 ~1초 절약 (TTL 5분 대비 무시 가능). + HTTP 오버헤드 감소.
- SSE의 비용: TCP 연결 유지, SSE 관리 로직, 스케줄러 구현.

---

## 7. SSE Nice-to-Have — 하이브리드 방식 (상위 100명만 실시간)

**의사결정 흐름 (3단계 변천):**

1차: "SSE로 전체 순번 Push" → 100만명에 불가능
→ 2차: "스케줄러 기반 SSE Push (방법 A/B/C 비교)" → 서버 측 Polling과 동일, SSE 가치 없음
→ 3차 최종: "상위 100명만 ZPOPMIN 트리거로 실시간 Push + 나머지 Polling"

**결론**: Queue는 분리하지 않고, Push 대상만 좁힘.

```
토큰 발급 스케줄러 (100ms마다):
  1. ZPOPMIN 18명 → 토큰 발급 → SSE로 즉시 Push
  2. ZRANGE 0 99 → 상위 100명에게 순번 Push
비용: Redis 2회 + SSE send ~100회 = 매우 가벼움
```

### SSE 연결 시점 — 동적 계산

고정 임계값(position ≤ 2500)은 Polling 주기나 TPS 변경 시 재계산 필요. 동적 계산으로 자동 적응:

```
매 Polling 응답 시:
  nextPollETA = (position / 175) - currentPollingInterval
  if (nextPollETA <= 15초): 응답에 SSE 연결 힌트 포함
```

- SSE 연결 직후 snapshot 1회 즉시 전송 (순번 + ETA + 토큰 여부)
- SSE 연결 수: 최대 수천 개 (100만 idle 연결과 차원이 다름)

### 주의사항 (Codex 피드백)

| 항목 | 대응 |
|------|------|
| 멀티 인스턴스 | sticky session 또는 Redis Pub/Sub 필요 |
| 토큰 발급 레이스 | ZPOPMIN → SET 사이에 ZRANK null + 토큰 없음 가능. Lua script 검토 |
| 스케줄러 책임 | 발급(핵심)과 Push(I/O)를 느슨하게 분리 |
| SSE 운영 | heartbeat, reconnect, emitter cleanup, LB timeout |

---

## 8. SSE Push 스케줄러 구조 비교 — A/B/C (SSE 전체 Push 시 검토, 최종 불채택)

> 이 섹션은 "SSE로 전체 순번을 Push한다"는 전제에서의 검토 기록이다.
> 최종안은 "상위 100명만 ZPOPMIN 트리거로 Push"이므로 이 스케줄러 구조는 불채택되었다. 참고용.

**비교한 3가지:**

| 기준 | A (1초 전체 순회) | B (min-heap) | C (구간별 스케줄러) |
|------|:---:|:---:|:---:|
| 구조 | Map 1개, 스케줄러 1개 | PriorityQueue, 스케줄러 1개 | Map 3개, 스케줄러 3개 |
| 동시성 | 없음 | 없음 | 구간 이동 시 유실/중복 |
| 순회 비용 (100만명) | ~50ms | Push 대상만 | 구간별만 |
| 구간 변경 반영 | 즉시 | 지연 (최대 60초) | 지연 |
| 순간 부하 집중 | per-user 분산 | 초기 Jitter로 완화 | tick마다 burst |

**결론**: A가 가장 합리적이었으나, SSE 전체 Push 자체가 불채택됨.

- B(min-heap): 구간 변경 반영 지연, 유저 제거 비효율. 50ms 순회를 줄이려다 더 큰 문제 발생.
- C(구간별 분리): 동시성 문제, burst, 승격 지연. 관리포인트 급증.
- Codex(gpt-5.4) 의견도 A 추천: "병목은 순회가 아니라 Redis + SSE send. A의 in-memory 비교는 비용 0."

---

## 9. Polling 구간별 주기 설계 — 부하 vs UX 균형

**최종 구간:**

| 순번 구간 | 방식 | 주기 | Jitter | 초당 요청 수 |
|-----------|------|------|--------|-------------|
| 1~100 | SSE 실시간 | 100ms (ZPOPMIN 트리거) | 없음 | ~100 |
| 101~1,000 | Polling | 2초 | ±0.3초 | 450 |
| 1,001~10,000 | Polling | 5초 | ±0.5초 | 1,800 |
| 10,001~100,000 | Polling | 10초 | ±1초 | 9,000 |
| 100,001+ | Polling | 30초 | ±4초 | 30,000 |

합계: ~41,350 req/s

**검토한 대안들:**

| 방안 | 주기 | 총 req/s | 비고 |
|------|------|----------|------|
| 기존 (3,10,30,60) | 최대 60초 | ~19,000 | UX 다소 느림 |
| exponential (2,4,8,16) | 최대 16초 | ~70,000 | 뒷구간 부하 과다 |
| exponential + 구간 쪼개기 (2,4,8,16,32,64) | 최대 64초 | ~42,000 | 구간 7개로 과다 |
| **최종안 (2,5,10,30)** | 최대 30초 | **~41,350** | 앞쪽 UX↑ + 뒷구간 안정 |

- 근거: 앞쪽 구간(101~10000)은 곧 입장할 유저 → 짧은 주기로 체감 개선. 뒷구간(100,001+)은 예상 대기 9.5분+ → 30초 주기로 충분.
- 스케일아웃 환경(3대+)에서 서버당 ~14,000 req/s → 충분히 여유.
- Redis는 41,000 ZRANK/초를 단일 인스턴스로 감당 가능 (처리량의 14~41%).

### Jitter 적용 방식

- **서버가 retryAfterMs에 Jitter 포함**: 클라이언트는 값 그대로 사용. 서버가 완전히 제어.
- **초기 Jitter**: `POST /queue/enter` 응답의 retryAfterMs = `random(0, interval)`. 동시 진입 시 첫 Polling 분산.
- **이후 Jitter**: `GET /queue/position` 응답의 retryAfterMs = `interval + random(-jitter, +jitter)`.

---

## 10. 초기 burst 대응 — 스케일아웃

100만명이 동시에 `POST /queue/enter`를 호출하는 상황. Jitter로 해결할 수 없는 Wave 1(진입 요청 자체)이다.

```
Wave 1: POST /queue/enter × 100만 → Jitter 적용 불가 (동시 진입)
Wave 2: 첫 GET /queue/position   → 초기 Jitter로 분산 가능
Wave 3+: 이후 Polling              → 일반 Jitter로 분산 유지
```

**결론**: 스케일아웃으로 버틴다. (멘토님 답변)

- Kafka-first 아키텍처에서 enter API는 Kafka produce만 수행 (DB 접근 없음, 가벼움)
- 인증은 서명 기반 토큰으로 DB 조회 불필요
- 서버 3~5대로 분산 처리
- Kafka는 burst 흡수에 강하고, Redis보다 높은 throughput 제공

---

## 11. Graceful Degradation — Kafka-first 아키텍처 + Redis 장애 시 Kafka fallback

**의사결정 흐름 (3단계 변천):**

1차: "Redis 장애 시 전면 차단" → 가장 안전하지만 서비스 완전 중단
→ 2차: "Kafka fallback (근사 순번)" 검토 → "대기열이 정확한 실시간성을 보장해야 하는가?" 재질문
→ 3차 최종: "Kafka-first 아키텍처 도입" → 진입을 Kafka로 먼저 받고, Redis는 순번 조회용으로만 사용

**핵심 전환점**: 대기열 순번은 이미 ZPOPMIN으로 N명씩 점프하므로 정확한 실시간성이 아님.
유저도 정확한 1씩 감소를 기대하지 않음. 보수적 근사 순번으로 충분.

**Kafka-first 아키텍처:**

```
정상 운영:
  User → Kafka produce (acks=1) → "접수 완료" 응답
  Consumer → Kafka 읽기 → ZADD Redis (순번 조회용)
  Polling → Redis ZRANK → 순번 응답
  스케줄러 → ZPOPMIN → 토큰 발급

Redis 장애 시:
  User → Kafka produce → "접수 완료" (Kafka는 살아있으니 진입 가능)
  Consumer 일시 정지 (Redis 못 씀)
  Polling → Kafka offset 기반 근사 순번 계산 (보수적: 실제보다 느리게 표시)
  토큰 발급 → Consumer가 Kafka에서 읽어 직접 발급 (Redis 없이)

Redis 복구 시:
  Consumer가 밀린 Kafka 메시지 → Redis ZADD로 재적재
  → 정상 운영 복귀. Kafka에 원본이 있으므로 데이터 유실 없음.
```

**기존 선택지와 비교:**

| 전략 | 서비스 유지 | 데이터 보존 | 순번 정확성 | 복잡도 |
|------|:---:|:---:|:---:|:---:|
| 전면 차단 | ❌ | - | - | 낮음 |
| 우회 (bypass) | ✅ | ❌ | - | 낮음 |
| 로컬 Fallback 큐 | ✅ | ❌ | ❌ 서버별 불일치 | 중간 |
| **Kafka-first** | **✅** | **✅** | **△ 근사** | **중간** |

**결론**: Kafka-first 아키텍처 채택.

- 근거 1 — Kafka에 원본이 있으므로 Redis 장애 시에도 데이터 유실 없음.
- 근거 2 — 순번은 보수적 계산(120명/초)으로 근사 제공. 예상보다 빨리 입장하면 좋은 경험.
- 근거 3 — 주문 TPS(175)에 영향 없음. 스케줄러 ZPOPMIN 구조 동일.
- 근거 4 — 아웃박스 불필요. DB 상태와 원자적으로 맞춰야 할 대상이 없음. Kafka produce 실패 시 유저에게 에러 → 재시도.
- 전제: Redis Sentinel/Cluster로 고가용성 확보. 장애 자체를 최소화하는 것이 1순위.

---

## 12. 예상 대기 시간 — 보수적 TPS 기준

**결론**: `예상 대기 시간 = 순번 / 175` (안전 마진 적용 TPS)

- 이론적 최대(250)로 계산하면 "2초 남았다더니 10초" → 유저 불만.
- 보수적(175)으로 계산하면 "4초 남았는데 3초에 들어감" → 유저 만족.
- **예상보다 빨리 들어가는 건 좋은 경험, 늦게 들어가는 건 나쁜 경험.**

---

## 13. 멘토 질문 및 답변

### 질문 1: 100만명 Polling 부하 처리 방법

구간별 차등 주기를 적용해도 초당 약 4만 건의 순번 조회 요청 발생. 효율적으로 처리하는 방법은?

→ (답변 대기 중 또는 추가 예정)

### 질문 2: 초기 enter burst 대응

100만명 동시 진입 요청은 Jitter로 분산 불가. 어떻게 처리하는가?

→ **답변: 스케일아웃으로 버틴다.** enter API는 Redis만 사용하여 가볍고, 서버를 수평 확장하여 처리.

### 질문 3: 대기열에서 SSE의 실질적 가치

SSE로 서버가 Push해도 주기적 갱신이라 Polling과 차이 없음. 장점은 HTTP 오버헤드 감소, 토큰 즉시 전달 정도. 비용은 TCP 연결 유지, 관리 로직, 스케줄러 구현. 실무에서 SSE를 도입할 만한 이유가 있는가?

→ (답변 대기 중 또는 추가 예정)

---

## 14. 서명 기반 인증 토큰 — 대기열 전용 경량 인증

**결론**: 대기열 API 전용 HMAC 서명 토큰. Redis/DB 조회 없이 검증.

```
POST /auth/queue-token (DB 인증 1회):
  DB 인증 → userId
  token = userId + 만료시각 + HMAC(userId + 만료시각, secretKey)
  → 응답: { queueToken: "..." }

대기열 API (서명 검증만, I/O 없음):
  token에서 userId, 만료시각 추출 → HMAC 검증 → userId 반환
```

- 근거 1 — 현재 인증(X-Loopers-LoginId/LoginPw → DB 조회)을 대기열에 그대로 쓰면, 대기열이 보호하려는 DB를 대기열이 먼저 죽임.
- 근거 2 — Redis 세션 방식은 Redis 장애 시 인증 불가 → Kafka-first fallback 무력화. 서명 기반은 Redis 없이 검증 가능.
- 근거 3 — JWT와 거의 동일한 구조 → 추후 JWT 이식 시 QueueAuthPort 구현체만 교체.
- TTL 관리: 토큰 자체에 만료시각 포함. Polling 응답 시 갱신된 토큰 재발급 (HMAC 생성은 CPU 연산, 수 μs).

---

## 15. ZPOPMIN + SET + ZCARD → Lua script 원자화 (필수)

**결론**: 토큰 발급 스케줄러의 핵심 연산을 Lua script 하나로 묶어 원자적 처리.

```lua
-- cap 검사 + ZPOPMIN + SET을 하나의 원자 연산으로
local count = redis.call('ZCARD', 'waiting-queue')
-- cap 검사 생략 가능 (Kafka-first에서는 Consumer가 제어)
local users = redis.call('ZPOPMIN', 'waiting-queue', 18)
for i = 1, #users, 2 do
  local userId = users[i]
  redis.call('SET', 'entry-token:' .. userId, token, 'EX', 300)
end
return users
```

- 근거 1 — ZPOPMIN → SET 사이에 서버 죽으면 유저 영구 유실. "검토"가 아니라 필수.
- 근거 2 — ZCARD cap 검사도 같은 Lua에 포함하면 추가 비용 없이 원자적 cap 보장.

---

## 16. Score 설계 — Redis INCR 기반 단조 증가 순번

**결론**: score = Redis INCR (timestamp 대신)

- 근거 1 — timestamp는 동일 ms 충돌 시 member 사전순 정렬 → FIFO 깨짐.
- 근거 2 — 멀티 인스턴스 clock skew → 순서 역전 가능.
- 근거 3 — INCR은 원자적, 전역 단조 증가 → 엄밀한 FIFO 보장.
- Kafka-first에서: Consumer가 Kafka에서 읽은 순서대로 INCR + ZADD → Kafka 순서 + INCR = 완벽한 FIFO.

---

## 17. 스케줄러 단일 리더 — Redis 분산 락 (ShedLock)

**결론**: 토큰 발급 스케줄러는 ShedLock + Redis 분산 락으로 단일 인스턴스에서만 실행.

- 근거: 서버 N대에서 @Scheduled가 독립 실행되면 발급량이 N배 증가. ZPOPMIN은 중복 pop만 방지, 속도 증폭은 막지 못함. 목표 TPS 175가 깨짐.
