# Round 9 — Rebuild-Ready Ranking 설계 (v2)

> 04-decisions.md 의 후속 — Scorer 교체에도 견디는 재계산 가능한 랭킹 기반 설계.
> 가이드 + 1차 피드백을 반영한 확정안.

---

## 0. 문제 정의

| 영역 | 현재 | 한계 |
|---|---|---|
| 저장소 | Redis ZSET + HASH counter, TTL 2d | 2일 넘으면 재료 자체가 사라짐 |
| 날짜 키 | `LocalDate.now()` (consumer 처리 시점) | 지연 처리/리플레이 시 날짜 오염 |
| 재계산 단위 | 최종 score만 Redis에 존재 | Scorer 바꾸면 과거 재현 불가 |
| Carry-over | `ZUNIONSTORE` 기반 — 전날 ZSET 존재가 전제 | 장애/재기동 시 chain 손실 |
| event_log | 감사용 (logging BC) | 랭킹 재료로 설계된 구조 아님 |

→ **결론**: Scorer만 교체 가능하고, "재료(feature)" 와 "최종 점수 스냅샷"이 영속화되어 있지 않다. Redis는 서빙 캐시일 뿐이다.

---

## 1. 설계 원칙

1. **Redis는 projection(서빙 캐시)로 격하.** SoT는 DB.
2. **재료(view/like/order count)와 최종 점수(carry 포함)를 둘 다 일간으로 영속화.**
3. **이벤트 발생시각은 `KafkaEventEnvelope.occurredAt` (단일 기준)으로 결정.**
4. **DB 멱등 보장**: counter upsert + score upsert + event_handled insert 를 **단일 TX**로 묶는다. Redis는 그 뒤 best-effort.
5. **Rebuild는 daily_counter / daily_score만 읽어 계산.** Kafka replay 금지.
6. **source BC 불가침.** ranking storage 쓰기는 ranking consumer가 단독 책임.
7. **Carry-over는 daily_score 기반.** Redis 간 ZUNIONSTORE 의존 제거.
8. **count clamp는 scorer 입력 시점에만.** DB는 raw signed 값을 저장.

---

## 2. BC / 모듈 배치

```
ranking (BC)
├── commerce-streamer        # consumer dual-write (DB primary, Redis projection)
│   ├── application
│   │   ├── service/RankingScoreService              (TX 경계 변경)
│   │   └── port/out/
│   │       ├── RankingRedisPort                     (기존)
│   │       ├── RankingDailyCounterCommandPort       (신규)
│   │       ├── RankingDailyScoreCommandPort         (신규)
│   │       ├── RankingProjectionDirtyPort           (신규)
│   │       └── RankingScorer                        (기존)
│   ├── infrastructure
│   │   ├── redis/RankingRedisAdapter
│   │   ├── entity/
│   │   │   ├── RankingDailyCounterEntity            (신규)
│   │   │   ├── RankingDailyScoreEntity              (신규)
│   │   │   └── RankingProjectionDirtyEntity         (신규)
│   │   ├── jpa/                                     (3종 JpaRepository)
│   │   └── repository/                              (3종 CommandAdapter)
│   └── interfaces/consumer/RankingCollectorConsumer (occurredAt + DailyKey 합산)
│
├── commerce-api             # 조회 + carry-over 스케줄러
│   ├── application
│   │   ├── service/RankingCarryOverService          (DB 기반으로 재작성)
│   │   └── port/out/                                (조회용 projection)
│   └── infrastructure/redis (기존 master template 사용)
│
└── commerce-batch           # rebuild + reconcile job
    └── batch/job/ranking/
        ├── RankingRebuildJobConfig
        ├── step/RankingRebuildTasklet
        ├── RankingReconcileJobConfig
        └── step/RankingReconcileTasklet
```

- `ranking_daily_counter`, `ranking_daily_score`, `ranking_projection_dirty` Entity는 **streamer에 두고 쓰기 책임**. api/batch는 Projection record 로 읽기만.

---

## 3. 데이터 모델

### 3.1 `ranking_daily_counter` (재료)

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `stat_date` | DATE NOT NULL | 집계 날짜 (envelope.occurredAt KST) |
| `product_id` | BIGINT NOT NULL | 상품 ID |
| `view_count` | BIGINT NOT NULL DEFAULT 0 | 일간 조회수 (signed, raw net) |
| `like_count` | BIGINT NOT NULL DEFAULT 0 | 일간 좋아요 net (signed, unlike 시 음수 가능) |
| `order_qty` | BIGINT NOT NULL DEFAULT 0 | 일간 주문수량 (signed) |
| `updated_at` | DATETIME NOT NULL | 마지막 갱신 시각 |

- **PK**: `(stat_date, product_id)`
- **Index**: `(product_id, stat_date)` — 상품별 추이 조회용
- **clamp 없음**. raw signed 저장. clamp는 scorer 입력 직전.

### 3.2 `ranking_daily_score` (carry 포함 최종 점수 스냅샷)

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `stat_date` | DATE NOT NULL | 집계 날짜 |
| `product_id` | BIGINT NOT NULL | 상품 ID |
| `scorer_type` | VARCHAR(32) NOT NULL | `SATURATION` 등 |
| `organic_score` | DOUBLE NOT NULL DEFAULT 0 | 오늘 counter로 계산한 점수 |
| `carry_score` | DOUBLE NOT NULL DEFAULT 0 | 전날에서 carry 받은 점수 |
| `updated_at` | DATETIME NOT NULL | 마지막 갱신 시각 |

- **PK**: `(stat_date, scorer_type, product_id)`
- 최종 score = `organic_score + carry_score`
- consumer는 organic_score만 갱신. carry_score는 carry-over 스케줄러가 인서트(건드리지 않음).
- carry-over 시 어제 row 의 `(organic + carry) * 0.1` 을 내일 row 의 `carry_score` 에 insert.

### 3.3 `ranking_projection_dirty` (Redis 복구 큐)

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `stat_date` | DATE NOT NULL | 영향 받은 날짜 |
| `reason` | VARCHAR(32) NOT NULL | `REDIS_WRITE_FAIL` / `MANUAL` / `CARRY_OVER_FAIL` |
| `marked_at` | DATETIME NOT NULL | mark 시각 |
| `resolved_at` | DATETIME NULL | reconcile 완료 시각 |

- **PK**: `(stat_date, reason)`
- 같은 (date, reason) 으로 여러 번 mark되면 ON DUPLICATE KEY UPDATE 로 marked_at만 갱신.

### 3.4 `ranking_event_fact` (Phase 2 — MVP에서는 만들지 않음)

---

## 4. Consumer 변경

### 4.1 occurredAt 기준 날짜 결정

**단일 규칙**: `KafkaEventEnvelope.occurredAt` 을 `Asia/Seoul` LocalDate로 변환하여 stat_date로 사용.

```java
private static final ZoneId KST = ZoneId.of("Asia/Seoul");

LocalDate statDate = envelope.occurredAt()
    .atZone(KST)        // LocalDateTime → ZonedDateTime
    .toLocalDate();
```

- payload.occurredAt 사용 안 함 (envelope으로 통일)
- record.timestamp() fallback 없음 (envelope 역직렬화 실패면 즉시 throw)
- 같은 배치 안에 다른 stat_date 가 섞일 수 있으므로 합산 키를 변경:
  ```java
  record DailyKey(LocalDate statDate, Long productId) {}
  Map<DailyKey, long[]> deltas;
  ```

### 4.2 TX 경계

```
[step 1] DB 단일 TX (RankingScoreService.applyDeltas)
   ├ 1. ranking_daily_counter upsert        (배치 합산된 delta 반영)
   ├ 2. ranking_daily_score   upsert        (organic_score 재계산)
   └ 3. event_handled         bulk insert
   commit

[step 2] Redis (best-effort, TX 밖)
   try {
     for each (date, productId, counts) {
       HINCRBY counter:{type}:{date} → CounterResult
       ZINCRBY ranking:all:{date}    (scorer delta)
     }
     ensureTtl(date)
   } catch (Throwable t) {
     log.warn(...)
     dirtyPort.markDirty(affectedDates, REDIS_WRITE_FAIL)  // 별도 짧은 TX
   }

[step 3] ack (Redis 실패해도 ack — DB가 SoT)
```

**핵심**:
- Retry 시 event_handled 가 이미 commit 되어 있으므로 멱등 필터에 걸려 counter/score 재집계 차단 → **double-count 불가**.
- Redis는 실패해도 reconcile job이 daily_score → Redis ZSET 재생성 → 자동 수렴.
- DB upsert 후 Redis 가 늦게 보이는 약간의 read-after-write skew 는 허용 (랭킹 특성상 무의미).

### 4.3 organic_score upsert 규칙

```sql
INSERT INTO ranking_daily_score (stat_date, product_id, scorer_type, organic_score, carry_score, updated_at)
VALUES (?, ?, 'SATURATION', ?, 0, NOW())
ON DUPLICATE KEY UPDATE
  organic_score = VALUES(organic_score),
  updated_at    = NOW();
```

- consumer가 매 batch마다 `clamp(counter) → scorer` 로 새 organic_score 를 전체 재계산하여 덮어쓴다.
- carry_score 는 INSERT 시 0으로만 들어가고, UPDATE 시 절대 건드리지 않는다 (carry-over 스케줄러 전용).
- scorer_type은 현재 live scorer 한 종류만 (`@ConditionalOnProperty` 또는 단일 bean).

### 4.4 counter upsert 규칙

```sql
INSERT INTO ranking_daily_counter (stat_date, product_id, view_count, like_count, order_qty, updated_at)
VALUES (?, ?, ?, ?, ?, NOW())
ON DUPLICATE KEY UPDATE
  view_count = view_count + VALUES(view_count),
  like_count = like_count + VALUES(like_count),
  order_qty  = order_qty  + VALUES(order_qty),
  updated_at = NOW();
```

- clamp **없음** (signed 누적).
- 배치 내 같은 (date, productId) delta는 이미 메모리에서 합산된 상태로 한 번에 들어감.

---

## 5. Carry-over 재설계

### 5.1 변경 후 흐름 (`@Scheduled 23:50 KST`)

```
1. today    = LocalDate.now(KST)
2. tomorrow = today + 1
3. SELECT product_id, organic_score, carry_score
   FROM ranking_daily_score
   WHERE stat_date = today AND scorer_type = ?
   (cursor stream)
4. for each row:
     totalScore = organic_score + carry_score
     carryToTomorrow = totalScore * 0.1
     INSERT INTO ranking_daily_score
       (tomorrow, product_id, scorer_type, organic_score=0, carry_score=carryToTomorrow)
     ON DUPLICATE KEY UPDATE carry_score = VALUES(carry_score), updated_at = NOW()
5. Redis 반영:
     ZADD ranking:all:{tomorrow} carryToTomorrow productId
     EXPIRE ranking:all:{tomorrow} 2d
   try-catch + dirty mark on failure
```

- 0 활동 상품도 daily_score row에 살아있으므로 carry chain 이 끊기지 않음.
- carry_score 는 누적되지 않고 매일 새로 산정 (어제의 total → 오늘의 carry).
- Redis 실패는 reconcile에 위임.

### 5.2 위치

- `commerce-api/ranking/application/service/RankingCarryOverService` 기존 클래스 재작성.
- `commerce-api/ranking/application/scheduler/RankingCarryOverScheduler` 유지 (test profile 비활성 그대로).
- DB 조회 port 추가: `RankingDailyScoreQueryPort` (api 측, projection record 반환).
- DB 쓰기 port 추가: `RankingDailyScoreCarryWritePort` (api 측, carry_score upsert만 노출).

---

## 6. Rebuild 유스케이스

### 6.1 Admin API

```
POST /api-admin/v1/rankings/rebuild
{
  "from": "20260101",
  "to":   "20260110",
  "scorerType": "SATURATION",
  "carryOverWeight": 0.1,
  "dryRun": false
}
```

- `RankingRebuildFacade` (commerce-api) 가 batch JobLauncher 호출 → JobExecution id 반환.

### 6.2 Batch Job (commerce-batch)

```
RankingRebuildJob (Tasklet 기반, 날짜 순차 — chunk 아님)

for date in [from..to]:
  1. SELECT * FROM ranking_daily_counter WHERE stat_date = date  (cursor)
  2. 각 row → scorer.calculateScore(clamp(view), clamp(like), clamp(order))
  3. carry_score 계산:
       prevTotal = SELECT organic + carry FROM ranking_daily_score WHERE stat_date = date-1
       carry_score = prevTotal[productId] * carryOverWeight   (0 row 상품은 0)
  4. ranking_daily_score 전체 덮어쓰기 (DELETE+INSERT or upsert)
       organic_score = scorer 결과
       carry_score   = 위 계산값
  5. Redis 반영: ZADD ranking:all:{date} (organic+carry) productId
                 (기존 키는 DEL 후 ZADD batch — 재현성 보장)
  6. EXPIRE ranking:all:{date} 2d
```

- 오늘 날짜는 rebuild 범위에서 제외 (live consumer 충돌 방지).
- carry chain 보존을 위해 from-1 일자는 반드시 daily_score에 row 가 있어야 함 → 없으면 carry=0 으로 폴백 + warn 로그.
- dryRun=true 면 step 4,5,6 skip.

### 6.3 Scorer 교체

- live: 단일 bean (`@ConditionalOnProperty` 로 한 종류만 active).
- rebuild: `Map<String, RankingScorer>` 로 모든 구현체 주입 → request `scorerType` 으로 lookup.

---

## 7. Reconcile (Redis 복구)

### 7.1 Job

```
RankingReconcileJob (cron, 예: 매 5분)

1. SELECT stat_date FROM ranking_projection_dirty
   WHERE resolved_at IS NULL
2. for each dirty date:
     RankingRebuildService.rebuildDay(date, currentScorerType, dryRun=false)
       (rebuild logic의 단일 날짜 버전 재사용)
3. UPDATE ranking_projection_dirty SET resolved_at = NOW() WHERE stat_date = ?
```

- rebuild 의 dayLoop 함수를 reconcile에서 그대로 호출 → **코드 단일 경로**.
- consumer / carry-over / 운영자 manual mark — 셋 다 같은 큐로 수렴.

### 7.2 mark 시점

| 발생 위치 | reason |
|---|---|
| Consumer Redis 쓰기 catch | `REDIS_WRITE_FAIL` |
| Carry-over Redis 쓰기 catch | `CARRY_OVER_FAIL` |
| Admin API (운영자) | `MANUAL` |

---

## 8. 마이그레이션 / 단계

| Step | 내용 | 독립 커밋 |
|---|---|---|
| **S1** | envelope.occurredAt 기준 날짜 계산 + DailyKey 도입 (Redis만 쓰는 현 구조 그대로) | ✅ |
| **S2** | `ranking_daily_counter` Entity + JpaRepository + CommandAdapter | ✅ |
| **S3** | `ranking_daily_score` Entity + JpaRepository + CommandAdapter | ✅ |
| **S4** | `ranking_projection_dirty` Entity + JpaRepository + CommandAdapter | ✅ |
| **S5** | RankingScoreService TX 경계 재구성 (DB single TX + Redis best-effort + dirty mark) | ✅ |
| **S6** | Carry-over 를 daily_score 기반으로 재작성 | ✅ |
| **S7** | RankingRebuildService + commerce-batch Job | ✅ |
| **S8** | RankingReconcileJob (rebuild 재사용) | ✅ |
| **S9** | Admin Rebuild API | ✅ |

각 step 후 테스트 통과 → 별도 커밋. 완료 후 PR로 묶음.

---

## 9. 테스트 전략

| 레이어 | 테스트 |
|---|---|
| Unit | RankingScoreService — DB upsert + score upsert + handled insert 가 단일 TX, Redis는 별도 호출, 실패 시 dirty mark |
| Unit | DailyKey 분리 — 같은 배치에 두 날짜 이벤트 섞이면 각각 다른 row에 반영 |
| Unit | Carry-over — 어제 (organic, carry) → 오늘 carry_score 계산, 0 활동 상품 보존 |
| Unit | RebuildService — counter rows + 전날 score → 새 score row + ZSET 반영 |
| Integration (streamer) | TestContainers MySQL+Redis+Kafka → 이벤트 → DB row + Redis ZSET 검증 |
| Integration (streamer) | Redis 강제 fail → DB는 정상 + dirty mark row 생성 검증 |
| Integration (streamer) | 같은 eventId 재처리 → counter 중복 누적 없음 (멱등) |
| Integration (batch) | 프리셋 counter + 전날 score → rebuild → 예상 daily_score + ZSET 일치 |
| Integration (batch) | reconcile job → dirty row 소진 + Redis 복구 |
| E2E | 이벤트 → DB → Redis → /api/v1/rankings 응답 |
| E2E | /api-admin/v1/rankings/rebuild → 새 점수로 ZSET 반영 |

---

## 10. Edge cases

1. **음수 net count**: signed 저장. scorer 직전 `Math.max(0, x)`. 다시 양수로 회복 가능.
2. **하루 경계 이벤트**: envelope.occurredAt 이 23:59:58 → outbox relay 100ms 지연 → 여전히 23:59:58 로 집계 (envelope 기준 단일 규칙).
3. **Consumer lag rebuild 충돌**: rebuild 대상은 today 제외. 운영자가 lag 완료 확인 후 실행 (admin 책임).
4. **TTL 연장**: rebuild가 만든 ZSET TTL = 2d (live 와 동일). 더 길게 보고 싶으면 admin 옵션.
5. **scorer_type 변경 후 reconcile**: 새 scorer로 rebuild → daily_score 의 scorer_type 도 함께 바뀜. 구 scorer_type row 는 archive 또는 drop.
6. **`(date, productId)` 가 daily_score 에는 있는데 daily_counter 에는 없음**: carry-only 상품 (어제까지 인기, 오늘 0 활동). 정상 케이스 — rebuild 시 organic=0, carry=계산값.
7. **batch rewriteBatchedStatements**: JDBC URL 에 `rewriteBatchedStatements=true` 필요 — 추가 확인.

---

## 11. Phase 2 (MVP 이후)

- Redis key versioning (`ranking:all:{v}:{date}` + pointer swap)
- `ranking_event_fact` (raw event 영속화 — 감사/장기 replay)
- scorer 입력 feature가 더 늘어나면 `ranking_daily_counter` 는 JSON blob 또는 wide schema 로 재설계하거나, raw event 장기 보관 + backfill 경로를 붙이는 쪽으로 가야 함. 이건 단순 컬럼 추가가 아니라 별도 설계 과제다.
- Redis HASH counter → score delta 경로는 app layer 에서 counter 갱신 직후 `ZINCRBY` 직전까지 gap 이 있다. 필요하면 Lua snapshot update, DB absolute-score projection, 더 짧은 reconcile cadence 중 하나로 보강한다.
- Hourly window
- Multi-scorer 동시 운영 (A/B)
- daily_counter 90일 archive 정책

---

## 12. 한 줄 요약

**daily_counter (재료) + daily_score (carry 포함 스냅샷) + projection_dirty (복구 큐) 를 ranking BC 에 두고, consumer는 단일 TX로 DB에 쓰고 Redis는 best-effort projection 으로 격하한다. Carry-over와 Rebuild와 Reconcile은 daily_score 단일 경로로 수렴한다.**
