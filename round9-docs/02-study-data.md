# Round 9 Study Data - Redis ZSET 기반 랭킹 시스템

> 학습 진행 중 실시간 업데이트

---

## Keywords 목록

| # | Keyword | 학습 상태 | 최종 점수 |
|---|---------|----------|----------|
| 1 | Redis ZSET 구조 및 핵심 연산 | ✅ 완료 | 90 |
| 2 | 시간의 양자화 & Key 전략 | ✅ 완료 | 90 |
| 3 | 가중치 합산 (Weighted Sum) | ✅ 완료 | 90 |
| 4 | Kafka Consumer → ZSET 파이프라인 | ✅ 완료 | 90 |
| 5 | Ranking API 설계 | ✅ 완료 | 90 |
| 6 | 콜드 스타트 & Score Carry-Over | ✅ 완료 | 95 |

---

## 1. Redis ZSET 구조 및 핵심 연산

### 개념
- ZSET은 `(member, score)` 쌍으로 구성되며, score 기준으로 **항상 정렬된 상태**를 유지
- member는 유일(unique), score는 중복 가능한 부동소수점 숫자
- 랭킹에 적합한 이유: **유일성(Set)** + **정렬(Sorted)** 두 가지를 동시에 제공

### 핵심 연산

| 명령어 | 용도 | 시간복잡도 |
|--------|------|-----------|
| `ZINCRBY key increment member` | 기존 score에 increment 누적 (이벤트 발생 시 사용) | O(log N) |
| `ZADD key score member` | score를 지정값으로 설정 (덮어쓰기) | O(log N) |
| `ZREVRANGE key 0 N-1 WITHSCORES` | 높은 점수 순 Top-N 조회 (0-based 인덱스) | O(N) |
| `ZREVRANK key member` | 특정 멤버의 순위 조회 (0-based, 높은 점수가 0위) | O(log N) |
| `ZSCORE key member` | 특정 멤버의 score 조회 | O(1) |
| `ZCARD key` | 전체 멤버 수 조회 | O(1) |

### 핵심 포인트
- 이벤트 누적에는 `ZINCRBY` 사용 (덮어쓰기가 아닌 누적)
- `ZREV-` 접두사: 기본 정렬이 오름차순이므로 내림차순(높은 점수 = 1위)을 위해 필요
- `ZREVRANK` 반환값은 0-based → API 응답에서 **+1** 처리 후 반환
- Redis가 내부적으로 순위를 O(log N)에 계산하므로, application에서 전체 목록을 가져와 순위를 계산할 필요 없음

### 왜 ZSET인가?
- DB `ORDER BY` 대비: 데이터 증가에도 조회 성능 일정, 실시간 반영
- 정렬 내장으로 별도 인덱스 설정 불필요
- Top-N, 특정 멤버 순위, score 범위 검색 등 다양한 조회 지원

---

## 2. 시간의 양자화 & Key 전략

### 개념
- 랭킹을 하나의 ZSET에 영구 누적하면 **오래된 상품이 신상품보다 항상 유리** → 롱테일 문제
- 해결책: 집계 단위(일/주/월)별로 ZSET 키를 분리 → 모든 상품이 해당 기간 시작 시 **동일하게 0점에서 출발**

### Key 설계
- 형식: `ranking:all:{yyyyMMdd}`
- 예: `ranking:all:20250906` (9월 6일), `ranking:all:20250907` (9월 7일)
- 오늘 키에는 오늘 발생한 이벤트만 누적됨

### TTL 설계
- 일간 랭킹 기준 TTL = **2일** (시간 윈도우 1일의 약 2배)
- TTL을 정확히 1일로 설정하면 자정 직후 키 소멸 → 다음날 "어제 랭킹" API 조회 실패, Score Carry-Over 작업 실패
- 2일 TTL: 전날 키가 다음날 하루 종일 살아있도록 보장

---

## 3. 가중치 합산 (Weighted Sum)

### 개념
- 여러 지표(view/like/order)를 ZSET의 단 하나의 score로 합산하는 방법
- `총점 = W(view)*Count(view) + W(like)*Count(like) + W(order)*Score(order)`
- 가중치 합은 1.0이 되도록 설계하면 score를 기여 비율로 해석 가능

### 기본 가중치
| 지표 | Weight | Score 단위 | 이유 |
|------|--------|-----------|------|
| view | 0.1 | 이벤트 1건 = 1 | 가장 많이 발생하므로 낮은 가중치 |
| like | 0.2 | 이벤트 1건 = 1 | 구매 결정 관점에서 order보다 덜 중요 |
| order | 0.7 | price * amount | 매출 직결, 가장 중요한 KPI |

### log 적용 이유 (주문 score)
- `price * amount` raw 적용 시 → 주문 1건(10만원) = 70,000점, 조회 1만건 = 1,000점
- order 지표가 score를 독점 → view/like 가중치 설계가 무의미해짐
- `log(price * amount)` 적용 시 → 신호 간 스케일 균형
- log는 순위를 바꾸는 게 아니라 **세 신호가 균형 있게 score에 기여하도록 스케일 압축**

### 계산 시점
- **Kafka 컨슈머에서 계산 후 ZINCRBY** (조회 시 계산 금지)
- 역할 분리: producer는 이벤트 발행만, 집계/계산은 consumer 책임
- 성능: 쓰기(컨슈머)에서 계산 비용 지불 → 읽기(API)는 ZREVRANGE만 수행

---

## 4. Kafka Consumer → ZSET 파이프라인

### 흐름 (이벤트 1건 처리 순서)
1. `event_handled` 테이블에서 event_id 중복 여부 확인 → 중복이면 처리 스킵 (멱등성 보장, R7)
2. 이벤트 종류(view/like/order) 확인 → Weight * Score 계산
   - view: `0.1 * 1`
   - like: `0.2 * 1`
   - order: `0.7 * log(price * amount)`
3. `ZINCRBY ranking:all:{오늘날짜} {계산된값} {productId}` 실행 (키 없으면 자동 생성)
4. `TTL ranking:all:{오늘날짜}` 확인 → `-1`이면 `EXPIRE ranking:all:{오늘날짜} 172800` 설정
5. `event_handled`에 event_id 기록
6. Kafka offset commit (Spring Kafka에서 처리 완료 후 자동 커밋)

### TTL 설정 주의사항
- 매 이벤트마다 EXPIRE 호출 금지 → TTL이 계속 리셋되고 불필요한 Redis 호출 발생
- `TTL == -1` (만료 미설정) 일 때만 EXPIRE 설정 → 하루에 딱 한 번만 호출됨
- `EXPIRE key ttl NX` (Redis 7.0+): 만료가 없을 때만 설정하는 atomic 옵션

---

## 5. Ranking API 설계

### 엔드포인트
| 용도 | 엔드포인트 |
|------|-----------|
| 랭킹 목록 조회 | `GET /api/v1/rankings?date=yyyyMMdd&size=20&page=1` |
| 상품 상세 조회 (랭킹 포함) | `GET /api/v1/products/{id}` |

### 랭킹 목록 응답 구조
- **아이템 1건**: 상품 id, 썸네일, 이름, 가격 등 상품 정보 + 현재 순위(rank)
- **페이지 메타**: page, size, totalElements

### 랭킹 목록 내부 처리 흐름
1. `ZREVRANGE ranking:all:{date} 시작인덱스 끝인덱스 WITHSCORES` → productId + score 목록
2. id → score Map 생성, id 목록 추출
3. `idIn(idList)` batch 조회로 상품 정보 한 번에 조회 (N+1 방지)
4. Map 기반으로 상품 정보 + 순위 조합 후 반환

### 상품 상세 조회 시 랭킹 처리
- 기존 상품 조회 완료 후 `ZREVRANK ranking:all:{오늘날짜} productId` 호출
- 멤버 존재 시: 0-based 순위 반환 → +1 처리하여 응답
- 멤버 미존재 시: `null` 반환 (요구사항 명시)

---

## 6. 콜드 스타트 & Score Carry-Over

### 문제 정의
- 날짜 키가 바뀌는 순간 모든 상품이 0점에서 시작
- 어제 인기 있던 상품도 오늘 첫 이벤트 전까지는 랭킹에 존재하지 않음
- 자정 직후 유저가 랭킹을 조회하면 의미 없는(비어있거나 극소수만 있는) 랭킹을 보게 됨

### 해결 전략: Score Carry-Over
- 전날 점수의 일부(예: 10%)를 새 날짜 키로 미리 복사
- Redis 명령어: `ZUNIONSTORE ranking:all:20250907 1 ranking:all:20250906 WEIGHTS 0.1`
- carry-over 비율을 작게 유지하는 이유: 오늘의 실제 이벤트가 충분히 쌓이면 carry-over 점수를 자연스럽게 넘어서도록

### 스케줄러 실행 시각: 23:50
- **자정(00:00) 금지 이유**: 작업 완료 전 날짜가 바뀌어 일부 상품만 carry-over된 불완전한 상태가 됨
- **너무 이른 시각(예: 23:00) 금지 이유**: 이후 발생하는 이벤트(23:01~23:59)가 carry-over 값에 반영되지 않아 불정확
- 23:50 = 두 제약 사이의 균형점

### 실시간 Weight 조정
- "실시간" = 배포 없이 즉시 반영
- DB 컬럼으로 관리: 스케줄러 실행 시마다 DB에서 fresh하게 읽어옴 (캐싱 금지)
- Redis key로 관리: `SET ranking:config:carry-over-weight 0.1` → Admin API로 업데이트
- 핵심 원칙: **스케줄러 실행 시마다 값을 fresh하게 읽는다**
