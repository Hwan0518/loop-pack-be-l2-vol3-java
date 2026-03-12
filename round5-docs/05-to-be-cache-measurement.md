# TO-BE 캐시 성능 측정 결과

> 실측 재현 명령어: `./gradlew :apps:commerce-api:benchmarkTest --tests '*ProductApiPerformanceTest.measureApiCache_*'`

## 측정 환경

| 항목 | 값 |
|------|---|
| DB | MySQL 8.0 (TestContainers) |
| Cache | Redis (TestContainers) |
| 데이터 규모 | 10만 / 100만 / 1000만 |
| 브랜드 | 50개 균등 분포 |
| 인덱스 | `product_read_model` 복합 인덱스 12개 |
| 캐시 구조 | 2-Layer Cache (ID 리스트 + 상세) |
| 측정 레벨 | MockMvc 기반 API 전체 스택 |

## 현재 구현 기준 요약

### 캐시 키와 TTL

| 대상 | 캐시 키 패턴 | TTL | 비고 |
|------|-------------|-----|------|
| 상품 상세 | `product:v1:{productId}` | 2분 + jitter | PDP, PLP partial miss fallback 공용 |
| ID 리스트 | `products:ids:v1:{brandId\|all}:{sort}:{page}:{size}` | 3분 + jitter | 목록 정렬/필터 조합별 ID 캐시 |

### 스탬피드 보호

| 기법 | 현재 구현 |
|------|----------|
| TTL Jitter | 적용 |
| PER | 적용 |
| Cache Lock | `LocalCacheLock` + double-check |

### Write-Through 범위

| 변경 작업 | 상세 캐시 | ID 리스트 캐시 |
|----------|----------|---------------|
| 좋아요 증가/감소 | 갱신 | 갱신 안 함 (TTL 자연 만료 허용) |
| 재고 차감 | 갱신 | 갱신 안 함 |
| 가격 변경 | 갱신 | `PRICE_ASC` 목록 갱신 |
| 상품 생성/삭제 | 생성/삭제 | 영향 목록 갱신 |
| 브랜드명 변경 | 해당 브랜드 상품 상세 일괄 갱신 | 갱신 안 함 |

### 측정 방식

- `MISS`: warmup도 매번 Redis를 비운 뒤 수행하고, 측정 직전에도 Redis를 비운 뒤 1회 요청한다.
- `HIT`: 명시적으로 캐시를 한 번 채운 뒤 warmup 3회, 측정 5회를 수행한다.
- 단일/버스트/지속부하 모두 `2xx` 응답만 성공 샘플로 집계한다.
- 캐시 갱신은 현재 구현처럼 트랜잭션 내부 best-effort write-through 기준으로 측정했다.
- `afterCommit` 이벤트 전환은 이번 라운드 범위 밖 TODO다.

---

## 단일 API 요청

### Cache Miss

#### 목록 API (`GET /api/v1/products`)

| UC | 조건 | 정렬 | 10만 avg (ms) | 100만 avg (ms) | 1000만 avg (ms) |
|----|------|------|:---:|:---:|:---:|
| 1 | brandId=X | LATEST | **39.85** | **142.18** | **1166.02** |
| 2 | brandId=X | PRICE_ASC | **54.95** | **334.30** | **3884.57** |
| 3 | brandId=X | LIKES_DESC | **32.85** | **122.44** | **1160.68** |
| 4 | brandId=1 | LATEST | **25.41** | **22.51** | **51.69** |
| 5 | brandId=1 | PRICE_ASC | **21.50** | **26.85** | **97.49** |
| 6 | brandId=1 | LIKES_DESC | **24.95** | **20.77** | **44.58** |

#### 상세 API (`GET /api/v1/products/{id}`)

| UC | 10만 avg (ms) | 100만 avg (ms) | 1000만 avg (ms) |
|----|:---:|:---:|:---:|
| 상세: productId=1 | **9.50** | **6.41** | **6.32** |

- cold-cache 기준에서는 `brandId` 없는 목록이 여전히 DB 쿼리 비용의 영향을 크게 받는다.
- 특히 `PRICE_ASC`는 1000만건에서 **3.88s**까지 상승했다. 캐시를 “없애도 되는 수준”이라고 보기 어려운 이유다.
- 상세 miss는 PK lookup 기반이라 전 규모에서 **6~10ms**로 안정적이다.

### Cache Hit

#### 목록 API (`GET /api/v1/products`)

| UC | 조건 | 정렬 | 10만 avg (ms) | 100만 avg (ms) | 1000만 avg (ms) |
|----|------|------|:---:|:---:|:---:|
| 1 | brandId=X | LATEST | **5.99** | **6.10** | **5.15** |
| 2 | brandId=X | PRICE_ASC | **7.65** | **7.39** | **6.42** |
| 3 | brandId=X | LIKES_DESC | **7.41** | **5.67** | **5.17** |
| 4 | brandId=1 | LATEST | **7.17** | **6.45** | **4.85** |
| 5 | brandId=1 | PRICE_ASC | **6.23** | **5.21** | **4.98** |
| 6 | brandId=1 | LIKES_DESC | **5.76** | **4.66** | **4.53** |

#### 상세 API (`GET /api/v1/products/{id}`)

| UC | 10만 avg (ms) | 100만 avg (ms) | 1000만 avg (ms) |
|----|:---:|:---:|:---:|
| 상세: productId=1 | **4.98** | **4.77** | **3.85** |

- hot-cache 경로는 전 규모에서 **3.85~7.65ms** 범위로 수렴했다.
- 즉, 현재 캐시의 진짜 가치 는 “cold-cache를 빠르게 만드는 것”보다 “hot-path를 데이터 규모와 분리하는 것”에 있다.

---

## 버스트 측정 (100 concurrent)

### Cache Hit

| 데이터 규모 | UC | 완료/전체 | 에러 | avg (ms) | p95 (ms) |
|:---:|------|:---:|:---:|:---:|:---:|
| 10만 | 목록 UC1: brandId=X, LATEST | 100/100 | 0 | **121.32** | **183.20** |
| 10만 | 목록 UC3: brandId=X, LIKES_DESC | 100/100 | 0 | **183.83** | **247.96** |
| 10만 | 목록 UC4: brandId=1, LATEST | 100/100 | 0 | **100.70** | **145.90** |
| 10만 | 상세: productId=1 | 100/100 | 0 | **51.90** | **101.51** |
| 100만 | 목록 UC1: brandId=X, LATEST | 100/100 | 0 | **215.73** | **258.76** |
| 100만 | 목록 UC3: brandId=X, LIKES_DESC | 100/100 | 0 | **213.48** | **255.92** |
| 100만 | 목록 UC4: brandId=1, LATEST | 100/100 | 0 | **93.50** | **133.44** |
| 100만 | 상세: productId=1 | 100/100 | 0 | **77.10** | **111.47** |
| 1000만 | 목록 UC1: brandId=X, LATEST | 100/100 | 0 | **1245.17** | **1287.48** |
| 1000만 | 목록 UC3: brandId=X, LIKES_DESC | 100/100 | 0 | **1287.17** | **1331.43** |
| 1000만 | 목록 UC4: brandId=1, LATEST | 100/100 | 0 | **158.50** | **200.02** |
| 1000만 | 상세: productId=1 | 100/100 | 0 | **48.26** | **72.73** |

### Cache Miss + 스탬피드 보호

| 데이터 규모 | UC | 완료/전체 | 에러 | avg (ms) | p95 (ms) |
|:---:|------|:---:|:---:|:---:|:---:|
| 10만 | 목록 UC1: brandId=X, LATEST | 100/100 | 0 | **225.83** | **269.18** |
| 10만 | 상세: productId=1 | 100/100 | 0 | **108.51** | **170.80** |
| 100만 | 목록 UC1: brandId=X, LATEST | 100/100 | 0 | **216.68** | **258.23** |
| 100만 | 상세: productId=1 | 100/100 | 0 | **62.30** | **93.85** |
| 1000만 | 목록 UC1: brandId=X, LATEST | 100/100 | 0 | **1145.78** | **1172.37** |
| 1000만 | 상세: productId=1 | 100/100 | 0 | **58.49** | **85.97** |

- same-key miss burst는 모두 **에러 0%**로 종료됐다.
- `LocalCacheLock` + double-check 덕분에 loader 중복 실행은 1회로 수렴하고, 나머지 요청은 lock 해제 후 캐시를 재사용한다.
- 다만 1000만건 전체 목록은 cache hit 버스트조차 **1.2초대**까지 올라간다. 이건 Redis가 느린 게 아니라, API 레이어 직렬화/역직렬화와 100 concurrent MockMvc 환경 비용이 누적된 결과다.

---

## 지속 부하 측정 (20 RPS × 10초, Cache Hit)

| 데이터 규모 | UC | 완료/전체 | 에러 | 실제 QPS | avg (ms) | p95 (ms) |
|:---:|------|:---:|:---:|:---:|:---:|:---:|
| 10만 | 목록 UC1: brandId=X, LATEST | 200/200 | 0 | **20.0** | **8.34** | **14.42** |
| 10만 | 목록 UC3: brandId=X, LIKES_DESC | 200/200 | 0 | **20.0** | **9.13** | **9.66** |
| 10만 | 목록 UC4: brandId=1, LATEST | 200/200 | 0 | **20.0** | **7.35** | **9.78** |
| 10만 | 상세: productId=1 | 200/200 | 0 | **20.0** | **6.16** | **8.36** |
| 100만 | 목록 UC1: brandId=X, LATEST | 200/200 | 0 | **20.0** | **6.67** | **8.18** |
| 100만 | 목록 UC3: brandId=X, LIKES_DESC | 200/200 | 0 | **20.0** | **8.34** | **10.47** |
| 100만 | 목록 UC4: brandId=1, LATEST | 200/200 | 0 | **20.0** | **7.17** | **11.14** |
| 100만 | 상세: productId=1 | 200/200 | 0 | **20.0** | **6.07** | **8.92** |
| 1000만 | 목록 UC1: brandId=X, LATEST | 200/200 | 0 | **20.0** | **6.62** | **8.96** |
| 1000만 | 목록 UC3: brandId=X, LIKES_DESC | 200/200 | 0 | **20.0** | **6.22** | **7.44** |
| 1000만 | 목록 UC4: brandId=1, LATEST | 200/200 | 0 | **20.0** | **6.42** | **8.13** |
| 1000만 | 상세: productId=1 | 200/200 | 0 | **20.0** | **5.91** | **7.40** |

- hot-cache sustained load에서는 전 규모 모두 **20.0 QPS / 에러 0%**를 유지했다.
- 즉, 운영상 중요한 steady-state는 현재 캐시 구조로 충분히 방어된다.

---

## 핵심 관찰

### 1. hot-cache는 데이터 규모와 거의 무관하다

- 단일 요청 기준 `3.85~7.65ms`
- 지속 부하 기준 `5.91~9.13ms`
- 10만, 100만, 1000만 모두 steady-state에서는 거의 같은 응답 구간에 들어온다.

### 2. cold-cache는 여전히 DB 비용에 종속된다

- `brandId` 없는 `PRICE_ASC`는 10만 `54.95ms` → 100만 `334.30ms` → 1000만 `3884.57ms`
- 즉, “인덱스가 있으니 캐시 없이도 충분하다”는 결론은 성립하지 않는다.

### 3. 스탬피드 보호는 same-key miss에서 의미 있게 동작한다

- miss burst 대표 시나리오에서 에러율은 모두 `0%`
- loader 중복 실행은 `LocalCacheLock`과 `double-check`로 제어된다.

### 4. 이번 수치는 이전 문서보다 MISS가 더 높다

- 이유는 기존 문서가 warmup 과정에서 이미 캐시를 채운 뒤 `MISS`를 측정했기 때문이다.
- 이번 재측정은 warmup과 측정 모두 Redis를 비워서 **실제 cold-cache miss**만 집계했다.

---

## 요약

| 지표 | 현재 결론 |
|------|----------|
| 단일 요청 Hit | 전 규모 `3.85~7.65ms` |
| 단일 요청 Miss | 쿼리 조건에 따라 `6.32ms ~ 3884.57ms` |
| 버스트 에러율 | hit/miss 모두 `0%` |
| 지속 부하 | 전 규모 `20.0 QPS`, 에러 `0%` |
| 설계 핵심 가치 | steady-state를 데이터 규모와 분리 |
| 남은 TODO | `afterCommit` 이벤트 기반 캐시 갱신 전환 |

> 상세 비교는 [`03-as-is-performance-measurement.md`](./03-as-is-performance-measurement.md), [`04-to-be-index-measurement.md`](./04-to-be-index-measurement.md)와 함께 읽는다.
