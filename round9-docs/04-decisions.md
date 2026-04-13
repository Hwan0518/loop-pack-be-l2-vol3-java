# Round 9 Design Decisions - Redis ZSET 기반 랭킹 시스템

> 요구사항을 100% 만족시키기 위한 설계 판단 기록

---

## Decision 목록

| # | 판단 항목 | 상태 |
|---|----------|------|
| 1 | BC/패키지 구조 결정 | ✅ 완료 |
| 2 | ZSET Key & Score 설계 | ✅ 완료 |
| 3 | Kafka Consumer 설계 | ✅ 완료 |
| 4 | Ranking API 설계 | ✅ 완료 |
| 5 | 콜드 스타트 전략 | ✅ 완료 |

---

## 1. BC/패키지 구조 결정

### 결정: `ranking` BC 신규 생성

### 근거
- **변경 원인 분리**: 가중치 파라미터, 집계 로직 변경이 engagement(좋아요/브랜드좋아요)와 무관
- **생명주기 분리**: 랭킹 집계 주기(일간)와 engagement 도메인의 생명주기가 다름
- **장애 전파 방지**: 랭킹 장애가 engagement 기능에 영향을 주어선 안 됨
- **집계 성격**: 여러 BC(catalog/engagement/ordering)의 신호를 집계한 결과 → 어느 한 BC에 귀속시키기 부적절

### 앱별 역할
| 앱 | ranking BC 역할 |
|----|----------------|
| `commerce-api` | Ranking 조회 API 제공, ZSET 조회, 상품 메타데이터 조립 후 응답 |
| `commerce-streamer` | Kafka 이벤트 수신, 가중치 계산, ZINCRBY로 ZSET 실시간 업데이트 |

---

## 2. ZSET Key & Score 설계

### Key 설계
- 형식: `ranking:all:{yyyyMMdd}` (요구사항 그대로 확정)
- `all` = 종합 랭킹 (단일 지표 랭킹과 구분)
- TTL: 2일 (요구사항 명시)

### Score 모델: Saturation 채택
**공식**: `sat(x, k) = x / (x + k)`

```
dailyScore(product) =
  0.15 * sat(viewCountDaily, 100) +
  0.35 * sat(likeCountDaily, 10) +
  0.50 * sat(orderQtyDaily, 3)
```

**min-max 대신 saturation을 택한 이유**:
- 개념상 정규화의 기본 후보는 min-max
- 단, 실시간 ZINCRBY 구조에서 min-max는 이벤트 1건마다 전체 상품 재계산(쓰기 증폭) 필요 → 부적합
- saturation: 각 상품이 독립적으로 계산 → 온라인 업데이트 가능 (O(1) per event)
- log/revenue 기반: "오늘의 인기상품"은 인기도(얼마나 많이 팔렸나) 기준이므로 quantity 사용. 매출 기여 기준 랭킹은 별도 admin용 집계로 분리하는 게 맞음

**k값 설계 근거**:
- view k=100: 행위 비용 0원, 비회원도 가능 → 발생 빈도 압도적으로 높음. 100회는 "꽤 관심받는" 기준
- like k=10: 로그인 필요, 의식적 행동 → 10개면 강한 참여 신호
- order k=3: 실제 비용 발생, 가장 depth 깊은 행위 → 3건이면 매우 강한 구매 신호

**order 기반 지표**: `orderQtyDaily` (수량 기반) — "오늘의 인기상품"은 인기도(얼마나 많이 팔렸나) 기준. 매출 기여 랭킹과 분리.

### 카운터 보조 키
saturation delta 계산을 위해 일간 카운터를 Redis HASH로 별도 유지
```
ranking:counter:view:{yyyyMMdd}   // HASH, productId → 일간 조회수
ranking:counter:like:{yyyyMMdd}   // HASH, productId → 일간 좋아요수
ranking:counter:order:{yyyyMMdd}  // HASH, productId → 일간 주문수량
```
- TTL: 2일 (ZSET과 동일)
- 실시간 업데이트: `HINCRBY`로 카운터 갱신 후 delta = sat(new) - sat(old) 계산 → `ZINCRBY`
- 이 경로는 counter 갱신과 `ZINCRBY` 사이가 앱 레이어에 걸쳐 있어 완전한 원자성은 아니다. 운영 보강이 필요하면 Lua snapshot update, DB 절대값 기반 projection, 짧은 reconcile 주기로 보완한다.

---

## 3. Kafka Consumer 설계

### 결정: 배치 리스너 + 상품별 delta 합산

### 처리 흐름
```
1. batch listener로 이벤트 수신
2. event_handled로 멱등 필터 (이미 처리된 eventId 제거)
3. 배치 내부에서 Map<productId, RankingDelta>로 합산
4. 상품별 daily counter HASH 갱신 (HINCRBY)
5. old/new count 차이로 deltaScore 계산 (saturation delta)
6. ZINCRBY ranking:all:{date} 수행
7. event_handled 일괄 기록 (bulk insert, 1 TX)
8. ack
```
- scorer 입력이 더 늘어나는 순간에는 counter 스키마를 넓히거나(raw/JSON 포함), 별도 raw event 장기 보관 + backfill 로 전환하는 수준의 설계 변경이 필요하다. 단순 컬럼 추가 문제로 보지 않는다.

### 배치 처리의 이점
| 항목 | 단건 처리 | 배치 처리 |
|------|---------|---------|
| Redis 쓰기 | 이벤트당 1회 | 배치 내 고유 상품 수만큼 |
| DB 쓰기 (event_handled) | 이벤트당 1 TX | 배치당 1 TX (bulk insert) |
| Kafka ack | 이벤트당 1회 | 배치당 1회 |

### 배치 최적화 한계 및 실제 효과
- worst case: 배치 내 모든 이벤트가 다른 상품 → Redis 쓰기 절감 없음
- 실제: 이커머스 이벤트는 멱법칙 분포 → 상위 상품에 이벤트 집중 → 집계 효과 큼
- Redis 쓰기 절감보다 DB TX 절감(event_handled bulk)과 ack 절감이 더 안정적인 이득

### 기존 패턴 재활용
- `ProductMetricsService.applyDeltasAndPublishSnapshots(Map<Long, long[]> deltas, ...)` 동일 패턴 적용

---

## 4. Ranking API 설계

### 엔드포인트
| API | 엔드포인트 |
|-----|-----------|
| 랭킹 목록 | `GET /api/v1/rankings?date=yyyyMMdd&size=20&page=1` |
| 상품 상세 (랭킹 포함) | `GET /api/v1/products/{id}` |

### 랭킹 기준 날짜
- 항상 오늘 키 기준 (`ranking:all:{오늘날짜}`)
- carry-over로 오늘 키가 이미 초기 점수를 갖고 출발 → "데이터 없음" 문제 없음

### Cross-BC 통신 설계

**A. `GET /products/{id}` — catalog → ranking**
- 역할: 상품 조회 결과에 랭킹 순위 추가
- Port: `catalog/application/port/out/client/ranking/ProductRankingReader`
- 구현체: `catalog/infrastructure/acl/ranking/ProductRankingReaderImpl`
- 조회: `ZREVRANK ranking:all:{오늘날짜} productId` → null이면 rank=null 반환

**B. `GET /rankings` — ranking → catalog**
- 역할: 랭킹 목록 조회 시 상품 상세 정보 조합
- Port: `ranking/application/port/out/client/catalog/RankingProductReader`
- 구현체: `ranking/infrastructure/acl/catalog/RankingProductReaderImpl`
- 조회: `idIn(idList)` batch 조회로 상품 정보 한 번에 가져옴 (N+1 방지)

### 랭킹 목록 응답 구조
- 아이템: 상품 id, 이름, 썸네일, 가격 등 + rank
- 페이지 메타: page, size, totalElements

---

## 5. 콜드 스타트 전략

### 결정: 23:50 Score Carry-Over 스케줄러

### carry-over 공식
```
ZUNIONSTORE ranking:all:{오늘날짜} 1 ranking:all:{어제날짜} WEIGHTS 0.1
```
- 전날 점수의 10%를 오늘 키로 복사
- 오늘 실제 이벤트가 쌓이면 carry-over 점수를 자연스럽게 추월

### 타이밍 결정: 23:50

| 방식 | 트레이드오프 |
|------|-----------|
| 23:50 스케줄러 | 마지막 10분 이벤트 carry-over 미반영. 단, 유저가 인지 불가 → UX 영향 없음 |
| 00:00:05 스케줄러 | 데이터 정확하지만 자정 직후 5초 + carry-over 실행 중 공백 발생 |
| Lazy init | 스케줄러 불필요. 단, 자정 이후 첫 요청 유저가 carry-over 대기 + 동시 요청 시 중복 실행 위험 |

**선택 근거**: 랭킹 시스템의 목적은 유저 관심 유도. carry-over 대기 시간은 UX에 직접 영향 → lazy init 제외. 마지막 10분 누락은 유저가 인지 불가 → 23:50 채택.

### carry-over의 역할
- 콜드 스타트 완화: 자정 이후 오늘 키가 비어있지 않도록 초기 점수 제공
- continuity 보정: tumbling window가 끊길 때 어제 트렌드의 관성을 일부 유지
