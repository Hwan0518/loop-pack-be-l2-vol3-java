# 성능 개선 현황 분석

## 목표 ① 상품 목록 조회 성능 개선

### 현재 상황

| 항목 | AS-IS |
|------|-------|
| 조회 API 목적 | 사용자 상품 목록 조회 (`GET /api/v1/products`) |
| 주요 조회 조건 | `deleted_at IS NULL` + 선택적 `brand_id` 필터 |
| 사용한 테이블/데이터 | `products` LEFT JOIN `brands` |
| 해당 테이블의 인덱스 | **PK(id)만 존재. brand_id, deleted_at, like_count, price, created_at 에 인덱스 없음** |
| 캐시 적용 여부 및 위치 | 없음 |
| 캐시 키 전략 | N/A |
| 캐시 TTL 가정 | N/A |

**현재 쿼리 구조** (`ProductQuerydslRepository.searchProducts`):
```sql
-- 데이터 쿼리
SELECT p.id, p.brand_id, b.name, p.name, p.price, p.stock, p.like_count
FROM products p LEFT JOIN brands b ON b.id = p.brand_id
WHERE p.deleted_at IS NULL [AND p.brand_id = ?]
ORDER BY p.like_count DESC  -- (or created_at DESC, price ASC)
OFFSET ? LIMIT ?

-- 카운트 쿼리 (별도 실행)
SELECT COUNT(*) FROM products WHERE deleted_at IS NULL [AND brand_id = ?]
```

**문제점**:
- 10만건 이상에서 `deleted_at IS NULL` 필터 → Full Table Scan
- `brand_id` 필터 + `like_count DESC` 정렬 → 인덱스 없이 filesort 발생
- 카운트 쿼리도 별도로 Full Table Scan
- `OFFSET` 기반 페이지네이션은 뒤 페이지로 갈수록 성능 급감 (skip scan)

### 유즈케이스별 조회 조건 매트릭스

총 6가지 조합이 발생하며, 각각에 대해 EXPLAIN 분석이 필요하다.

| # | brandId 필터 | 정렬 조건 | WHERE 절 | ORDER BY |
|---|:---:|---|---|---|
| 1 | X | LATEST (기본) | `deleted_at IS NULL` | `created_at DESC` |
| 2 | X | PRICE_ASC | `deleted_at IS NULL` | `price ASC` |
| 3 | X | LIKES_DESC | `deleted_at IS NULL` | `like_count DESC` |
| 4 | O | LATEST | `deleted_at IS NULL AND brand_id = ?` | `created_at DESC` |
| 5 | O | PRICE_ASC | `deleted_at IS NULL AND brand_id = ?` | `price ASC` |
| 6 | O | LIKES_DESC | `deleted_at IS NULL AND brand_id = ?` | `like_count DESC` |

> **EXPLAIN 분석 계획**: 인덱스 적용 전(AS-IS)과 적용 후(TO-BE) 각 6개 유즈케이스에 대해 `EXPLAIN ANALYZE`를 실행하고, `type`, `rows`, `Extra` (filesort/Using index 여부), 실행 시간을 비교한다.

### 설계 방향

| 항목 | TO-BE |
|------|-------|
| 조회 API 목적 | 동일 |
| 주요 조회 조건 | 동일 (brand_id 필터 + 정렬) |
| 사용한 테이블/데이터 | 동일 |
| 해당 테이블의 인덱스 | 복합 인덱스 추가 필요 — 정렬 타입별로 covering 가능한 인덱스 설계 |
| 캐시 적용 여부 및 위치 | 목표 ③에서 별도 처리 |
| 캐시 키 전략 | 목표 ③에서 별도 처리 |
| 캐시 TTL 가정 | 목표 ③에서 별도 처리 |

**인덱스 후보 (EXPLAIN 분석 대상)**:

| 인덱스 | 커버하는 유즈케이스 | 비고 |
|--------|:---:|---|
| `(brand_id, deleted_at, like_count)` | #3, #6 | 좋아요 순 정렬 (핵심) |
| `(brand_id, deleted_at, created_at)` | #1, #4 | 최신순 정렬 |
| `(brand_id, deleted_at, price)` | #2, #5 | 가격순 정렬 |

> **컬럼 순서 결정 원칙 — 카디널리티 우선**: `brand_id`(수십~수백 distinct)가 `deleted_at`(2 distinct: NULL/timestamp)보다 카디널리티가 높으므로 선두 컬럼에 배치. 두 컬럼 모두 equality 조건으로 사용되므로 인덱스 동작에는 영향 없으나, B-tree fan-out이 더 균등해짐.
>
> brandId 필터 없는 케이스(#1, #2, #3)에서는 인덱스 선두 컬럼(`brand_id`)을 사용할 수 없으므로 별도의 2-column 인덱스 `(deleted_at, sort_col)` 필요.

### 구조적 리스크 분석

**1. 설계가 성립하기 위해 반드시 참이어야 하는 전제 조건**
- `deleted_at IS NULL`인 행이 전체의 대다수(90%+)라면 인덱스 필터링 효과가 약해짐. Partial Index가 없는 MySQL에서는 `deleted_at` 컬럼을 인덱스 선두에 두되, `IS NULL` 조건이 인덱스 range scan을 탈 수 있어야 함
- `brand_id`의 cardinality가 충분히 높아야 인덱스 selectivity가 의미 있음. 브랜드 수가 10개 미만이면 인덱스 효과 미미
- 정렬 타입이 3개(LATEST, PRICE_ASC, LIKES_DESC)이므로 인덱스를 3벌 만들거나 trade-off를 감수해야 함

**2. 트래픽 10배 증가 시 가장 먼저 병목이 될 지점**
- **카운트 쿼리**. 데이터 쿼리는 LIMIT으로 제한되지만 `COUNT(*)`는 조건에 맞는 전체 행을 스캔함. 10만건에서 100만건으로 증가하면 카운트 쿼리가 선형적으로 느려짐
- `OFFSET` 페이지네이션의 뒤 페이지 (offset=90000 → 9만건 skip)

**3. 캐시 적중률 30% 이하 시 문제**
- (목표 ③과 연관) 캐시 miss 시마다 인덱스를 타지 못하는 쿼리가 DB에 직행. 인덱스 최적화가 선행되지 않으면 캐시 miss = Full Table Scan이 되어 DB 부하 급증

**4. 데이터 정합성이 깨질 수 있는 시나리오**
- **(a)** 관리자가 상품을 soft-delete한 직후, 캐시에 남아있는 목록에 삭제된 상품이 포함됨 (목표 ③과 교차)
- **(b)** 좋아요 수 UPDATE와 목록 조회가 동시에 발생 시, 정렬 순서가 일시적으로 stale한 `like_count` 기준으로 반환됨 (dirty read 수준이지만, 목록에서는 허용 가능할 수 있음)

**5. 가장 나중까지 미룰 수 있는 개선**
- `OFFSET` → cursor-based 페이지네이션 전환. 현재 프론트가 page 기반이면 즉시 변경 불가. 인덱스만 추가해도 10만건 수준에서는 충분

**6. 가장 먼저 손대야 할 위험 요소**
- **인덱스 부재 자체**. 10만건에서 `ORDER BY like_count DESC` + `WHERE deleted_at IS NULL`은 Full Table Scan + filesort가 확정적. 이것이 해결되지 않으면 캐시를 추가해도 miss 시 DB가 버티지 못함

---

## 목표 ② 좋아요 수 정렬 구조 개선

### 현재 상황

| 항목 | AS-IS |
|------|-------|
| 조회 API 목적 | 상품 목록의 좋아요 순 정렬 (`LIKES_DESC`) |
| 주요 조회 조건 | `ORDER BY like_count DESC` |
| 사용한 테이블/데이터 | `product_read_model.like_count` (비정규화 필드, Read Model 테이블에 존재) |
| 해당 테이블의 인덱스 | **`like_count`에 인덱스 없음** |
| 캐시 적용 여부 및 위치 | 없음 |
| 캐시 키 전략 | N/A |
| 캐시 TTL 가정 | N/A |

**핵심 발견: 비정규화는 이미 완료되어 있음**
- `ProductReadModelEntity`에 `like_count` 컬럼이 존재 (`product_read_model` 테이블)
- Read Model 생성 시 `likeCount = 0`으로 초기화
- 좋아요 등록/취소 시 `ProductReadModelJpaRepository`의 JPQL로 원자적 증감:
  ```sql
  UPDATE ProductReadModelEntity e SET e.likeCount = e.likeCount + 1 WHERE e.id = :id
  UPDATE ProductReadModelEntity e SET e.likeCount = e.likeCount - 1 WHERE e.id = :id AND e.likeCount > 0
  ```
- Cross-BC 흐름: `ProductLikeCommandFacade.createLike()` → 좋아요 저장 + `ProductLikeCountSyncer.increaseLikeCount()` → `ProductCommandFacade` → `ProductCommandService` → `ProductReadModelRepository` → JPQL UPDATE

**동기화 처리도 이미 구현됨**:
- 좋아요 생성 시: 같은 `@Transactional` 내에서 좋아요 INSERT + Read Model `like_count` INCREMENT 실행
- 좋아요 삭제 시: 같은 `@Transactional` 내에서 좋아요 DELETE + Read Model `like_count` DECREMENT 실행
- 상품 삭제 시: `ProductCommandFacade.deleteProduct()` → 좋아요 전체 삭제 (cleanup)

### 설계 방향

| 항목 | TO-BE |
|------|-------|
| 조회 API 목적 | 동일 |
| 주요 조회 조건 | 동일 |
| 사용한 테이블/데이터 | 동일 (이미 비정규화됨) |
| 해당 테이블의 인덱스 | `like_count` 포함 복합 인덱스 추가 (목표 ①과 병합) |
| 캐시 적용 여부 및 위치 | 목표 ③에서 처리 |
| 캐시 키 전략 | N/A |
| 캐시 TTL 가정 | N/A |

**TO-BE에서 추가로 필요한 것**:
- 비정규화 구조 자체는 완성. 남은 것은 **인덱스 추가** (목표 ①과 합류)
- `EXPLAIN` 전후 비교를 위한 10만건 데이터 준비
- 현재 `like_count`의 동기화 gap 시나리오 정리 (블로그용)

### 구조적 리스크 분석

**1. 설계가 성립하기 위해 반드시 참이어야 하는 전제 조건**
- 좋아요 등록/취소와 `like_count` 증감이 **반드시 같은 트랜잭션** 안에서 실행되어야 함. 현재 `ProductLikeCommandFacade`가 `@Transactional`이므로 성립. 만약 이벤트 기반 비동기로 전환하면 이 전제가 깨짐
- `likes` 테이블과 `products.like_count` 사이에 DB-level 제약(trigger 등)이 없으므로, **애플리케이션 레이어가 유일한 동기화 보장 수단**

**2. 트래픽 10배 증가 시 가장 먼저 병목이 될 지점**
- 좋아요 등록/취소의 `UPDATE products SET like_count = like_count + 1 WHERE id = ?`는 해당 row에 대한 **exclusive row lock**을 잡음. 특정 인기 상품에 좋아요가 몰리면(hotspot) 해당 row에 lock contention 발생
- 현재 `@Transactional`이 Facade 레벨이므로, 좋아요 INSERT + 카운트 UPDATE + 상품 존재 검증까지 하나의 TX에 묶여 lock 유지 시간이 길어질 수 있음

**3. 캐시 적중률 30% 이하 시 문제**
- `like_count` 자체는 products 테이블 row에 있으므로 목록 조회 시 추가 JOIN 불필요 (이미 해결됨). 캐시 miss가 발생해도 인덱스만 타면 DB 부하는 제한적
- 다만 인기 상품의 like_count가 빈번히 변경되면 캐시 무효화 빈도가 높아져 적중률 자체가 낮아지는 악순환 가능

**4. 데이터 정합성이 깨질 수 있는 시나리오**
- **(a) 애플리케이션 비정상 종료**: 좋아요 INSERT는 커밋되었으나 `like_count` UPDATE 전에 프로세스가 죽는 경우 — 현재 같은 TX이므로 둘 다 롤백되어 안전. 그러나 **TX 분리를 도입하면 발생 가능**
- **(b) 수동 DB 조작**: DBA가 `likes` 테이블에서 직접 row를 삭제하면 `products.like_count`와 실제 `COUNT(*)` 불일치 발생. 보정 배치(reconciliation)가 없음
- **(c) 상품 삭제 후 복원**: soft-delete된 상품을 `restore()`할 경우, 삭제 시 좋아요가 일괄 hard-delete(`deleteAllByTargetId`)되었으므로 복원 후 `like_count`가 실제 0이어야 하나, `like_count` 필드가 이전 값을 유지할 수 있음 (현재 restore 시 like_count 리셋 로직 미확인)

**5. 가장 나중까지 미룰 수 있는 개선**
- `like_count` 보정 배치 (reconciliation cron). 현재 동기 TX 보장이 되므로 정합성 문제 발생 확률이 매우 낮아 미룰 수 있음

**6. 가장 먼저 손대야 할 위험 요소**
- **hotspot 상품의 row lock contention**. 인기 상품에 동시 좋아요 100건이 몰리면 `UPDATE ... WHERE id = ?`의 InnoDB row lock이 직렬화됨. 현재는 별도 대응 없음 (Redis counter 버퍼링, 비동기 합산 등 미적용)

---

## 목표 ③ 캐시 적용

> 이 절은 초기 캐시 설계 탐색 기록을 포함한다. 현재 구현의 최종 상태는 `2-Layer Cache(product:v1 / products:ids:v1)` + `상세 2분 / ID 리스트 3분 TTL` + `targeted write-through`이며, 상세 내용은 `05`, `06`, `07` 문서를 따른다.

### 현재 상황

| 항목 | AS-IS |
|------|-------|
| 조회 API 목적 | 상품 상세 (`GET /api/v1/products/{id}`), 상품 목록 (`GET /api/v1/products`) |
| 주요 조회 조건 | 상세: PK 조회, 목록: brandId + sortType + page + size |
| 사용한 테이블/데이터 | `products` + `brands` (LEFT JOIN) |
| 해당 테이블의 인덱스 | PK만 존재 |
| 캐시 적용 여부 및 위치 | **없음**. Spring Cache 미활성화. `@EnableCaching` 없음. `CacheManager` 빈 없음 |
| 캐시 키 전략 | N/A |
| 캐시 TTL 가정 | N/A |

**Redis 인프라 현황**:
- `modules/redis/` 모듈 존재, `spring-boot-starter-data-redis` 의존성 있음
- `RedisConfig`에서 Lettuce 기반 master-replica 구성 완료
- `RedisTemplate<String, String>` 빈 2개 (replica-preferred / master-only)
- **그러나 실제 비즈니스 로직에서 Redis를 사용하는 곳이 없음**
- 유일한 캐시는 `CaffeineCouponIssueDuplicateGuard` (로컬 인메모리, 쿠폰 전용)

### 설계 방향

| 항목 | TO-BE |
|------|-------|
| 조회 API 목적 | 동일 |
| 주요 조회 조건 | 동일 |
| 사용한 테이블/데이터 | 동일 |
| 해당 테이블의 인덱스 | 목표 ①에서 추가 |
| 캐시 적용 여부 및 위치 | **상품 상세**: Facade 레벨 (Product+Brand 조합). **상품 목록**: Service 레벨 (단일 도메인) |
| 캐시 키 전략 | **상세**: `product:v1:{productId}`. **목록 ID 리스트**: `products:ids:v1:{brandId\|all}:{sortType}:{page}:{size}` |
| 캐시 TTL | **상세**: 2분. **ID 리스트**: 3분 |
| 구현 방식 | RedisTemplate 직접 사용 (캐시 흐름 명시적 제어) |

#### 설계 결정 상세

##### (1) 캐시 적용 레이어 — API별 도메인 조합 분석

캐시 레이어는 "무엇을 캐싱하는가"에 따라 결정한다. 단일 도메인 데이터라면 Service, 여러 도메인을 조합한 결과라면 Facade에서 캐싱한다.

| API | Facade 내부 호출 | 도메인 수 | 캐시 레이어 | 근거 |
|-----|-----------------|:---------:|:----------:|------|
| `getProduct(id)` 상품 상세 | `ProductQueryService.findActiveById()` + `BrandQueryService.getBrandById()` | 2개 | **Facade** | Product + Brand 이름을 조합하여 `ProductDetailOutDto` 생성. Service 레벨에서는 완성된 결과를 캐싱할 수 없음 |
| `getProducts(...)` 상품 목록 | `ProductQueryService.searchProducts()` 만 호출 | 1개 | **Service** | QueryDSL LEFT JOIN으로 brand name 포함한 `ProductPageOutDto`를 한 번에 반환. Facade는 단순 위임만 수행 |

##### (2) TTL 설정 근거

**TTL 결정 시 판단 기준 (업계 공통, 우선순위순)**:

| 순위 | 기준 | 설명 |
|:---:|------|------|
| 1 | **데이터 변경 빈도** | 얼마나 자주 바뀌는가? (가장 중요) |
| 2 | **허용 가능한 staleness** | 비즈니스적으로 몇 분 전 데이터까지 사용자에게 보여줘도 괜찮은가? |
| 3 | **쿼리 비용** | cache miss 시 DB 쿼리가 얼마나 무거운가? (무거울수록 긴 TTL) |
| 4 | **트래픽 볼륨** | 트래픽이 높을수록 긴 TTL로 DB 보호 |
| 5 | **메모리 제약** | 긴 TTL = 더 많은 키 = Redis 메모리 사용량 증가 |

> 출처: AWS Database Caching Strategies, ByteByteGo, Redis 공식 블로그, 올리브영 테크블로그

**업계 일반적 TTL 참고값**:

| 데이터 유형 | 일반적 TTL | 출처 |
|------------|-----------|------|
| 상품 상세 (이름, 설명) | 5~15분 | AWS, ByteByteGo |
| 상품 목록 / 검색 결과 | 5~15분 | Redis 공식, Medium |
| 카테고리 / 브랜드 정보 | 30분~12시간 | Microsoft Dynamics 365 |
| 재고 / 가격 | 0~5초 또는 캐시 안함 | Amazon 사례 |

**현재 구현 TTL 결정**:

| 대상 | TTL | 판단 근거 |
|------|:---:|----------|
| **상품 상세** | **2분** | 좋아요/재고 변경 시 상세 write-through가 자주 발생하므로 TTL은 짧게 두고, write-through 실패 시 최대 stale window만 제한 |
| **ID 리스트** | **3분** | 정렬/필터 조합 키 수가 많아 빠른 메모리 회수가 필요하고, 일부 목록 stale은 trade-off로 허용 |

> 현재 구현은 실측 후 상세 2분 / ID 리스트 3분으로 고정했다. `afterCommit` 이벤트 전환과 TTL 재조정은 후속 TODO다.

##### (3) 무효화 전략 — Active Invalidation + Safety-Net TTL 병행

업계 표준은 **둘 다 쓰는 것**(defense-in-depth)이다. AWS, Netflix EVCache, Meta TAO 모두 이 조합을 사용한다.

| 역할 | 메커니즘 | 설명 |
|------|---------|------|
| **즉시 무효화** | 데이터 변경 시 `DEL key` | 상품 수정/삭제/좋아요 변경 시 해당 캐시 키를 즉시 삭제. 정합성 우선 |
| **안전망 TTL** | 키 생성 시 TTL 설정 | evict 실패/누락 시 최대 staleness를 TTL로 보장. 조회가 적은 키의 메모리를 자동 해제 |

**현재 구현의 캐시 갱신 트리거**:
- **상품 상세 캐시** (`product:v1:{id}`): 생성/수정/좋아요/재고/브랜드명 변경 시 write-through, 삭제 시 evict
- **ID 리스트 캐시** (`products:ids:v1:*`): 생성/삭제는 모든 정렬, 가격 변경은 `PRICE_ASC`만 targeted refresh
- **좋아요 변경**: 상세만 write-through, ID 리스트는 TTL 자연 만료 허용

> **왜 "delete"이지 "update"가 아닌가?** 캐시 값을 직접 갱신하면 두 개의 동시 쓰기가 race condition을 일으킬 수 있다. 삭제 후 다음 조회 시 DB에서 최신 데이터를 lazy load하는 것이 더 안전하다. (출처: AWS Cache-Aside Pattern, Redis 공식)

##### (4) thundering herd 방어 — 현재 구현

| 계층 | 전략 | 현재 상태 |
|:---:|------|----------|
| 1 | **TTL jitter** | 적용 |
| 2 | **PER** | 적용 |
| 3 | **LocalCacheLock + double-check** | 적용 |

> 분산 락(`RedisCacheLock`)은 구현체로만 남아 있고, 현재 런타임 경로는 `LocalCacheLock`이 `@Primary`다.

##### (5) 구현 방식 — RedisTemplate 직접 사용

`@Cacheable` 대신 `RedisTemplate`을 직접 사용한다.

| 판단 기준 | 결정 근거 |
|----------|----------|
| 캐시 흐름 가시성 | 캐시 저장/조회/삭제 시점이 코드에서 명시적으로 보여야 함 |
| fallback 제어 | Redis 장애 시 `try-catch`로 DB fallback을 직접 구현해야 함. `@Cacheable`은 예외 전파 제어가 제한적 |
| TTL 세밀한 제어 | API별로 다른 TTL(상세 2분, ID 리스트 3분) + jitter를 직접 적용 |
| 과제 학습자료 권장 | "캐시가 언제 저장되고 언제 무효화되는지를 정확히 알아야 합니다" — RedisTemplate 실습 추천 |

> 현재 구현은 `RedisTemplate<String, String>` + `ObjectMapper` 직렬화 조합을 사용한다.

**캐시 미스 시 정상 동작 전략 (Cache-Aside + Fallback)**:
- **일반 캐시 미스**: Cache-Aside 패턴 적용. 캐시에 없으면 DB 조회 → 결과를 캐시에 적재 → 응답 반환. 인덱스 최적화(목표 ①)가 선행되므로 DB 직접 조회 시에도 수용 가능한 응답 시간 보장
- **Redis 장애 (연결 불가/타임아웃)**: Redis 호출을 `try-catch`로 감싸서 예외 발생 시 캐시를 skip하고 DB에서 직접 조회. Redis 장애가 서비스 장애로 전파되지 않도록 격리 (Redis는 성능 개선 수단이지 필수 의존성이 아님)

### 구조적 리스크 분석

**1. 설계가 성립하기 위해 반드시 참이어야 하는 전제 조건**
- Redis 장애 시 fallback이 구현되어야 함. 캐시는 성능 최적화 수단이므로 Redis 없이도 서비스가 정상 동작해야 하며, 이를 위해 목표 ①의 인덱스 최적화가 반드시 선행되어야 함
- 캐시에 저장하는 DTO가 JSON 직렬화/역직렬화가 가능해야 함. 현재 Java `record`는 Jackson으로 처리 가능하나, `BigDecimal`, `ZonedDateTime` 등의 직렬화 정밀도 보장 필요
- 문자열 RedisTemplate + ObjectMapper 직렬화 조합이 안정적으로 동작해야 함

**2. 트래픽 10배 증가 시 가장 먼저 병목이 될 지점**
- **목록 캐시의 키 폭발 (key explosion)**. `brandId × sortType × page × size` 조합이 수천~수만 개가 될 수 있음. 예: 브랜드 100개 × 정렬 3종 × 페이지 500 = 15만 키. Redis 메모리와 eviction 정책이 문제가 됨
- **cold-cache worst-case**: `brandId` 없는 `PRICE_ASC`는 1000만건에서 3.88초까지 상승하므로, steady-state hit rate 관리가 더 중요해짐

**3. 캐시 적중률 30% 이하 시 문제**
- **thundering herd (cache stampede)**: 인기 키의 TTL 만료 시 수백 요청이 동시에 DB로 몰림. 다만 인덱스 최적화(목표 ①)가 완료되어 있으므로, Full Table Scan이 아닌 인덱스 스캔으로 처리되어 DB가 버틸 수 있음. 인덱스가 없다면 즉시 connection pool 고갈
- **목록 캐시는 태생적으로 적중률이 낮을 수 있음**: page 파라미터에 따라 키가 분산되고, 좋아요 변경마다 무효화되면 hit rate 30%도 어려울 수 있음. 상세 캐시는 상대적으로 hit rate가 높을 것

**4. 데이터 정합성이 깨질 수 있는 시나리오**
- **(a) 캐시 무효화 실패**: 상품 수정 TX는 커밋되었으나 Redis eviction 명령이 네트워크 오류로 실패. 이후 사용자가 stale 데이터를 TTL 만료까지 계속 보게 됨
- **(b) 좋아요 → 캐시 무효화 순서 역전**: 좋아요 TX 커밋과 캐시 무효화 사이에 다른 요청이 DB에서 조회하여 캐시를 갱신하면, 직후 무효화가 실행되어 **오히려 최신 캐시가 삭제됨** (ABA problem)
- **(c) 상세 캐시에 좋아요 수 포함**: `ProductDetailOutDto`에 `likeCount`가 있으므로 좋아요 변경마다 상세 캐시를 무효화해야 함. 누락 시 상세 페이지의 좋아요 수가 stale

**5. 가장 나중까지 미룰 수 있는 개선**
- **목록 캐시**. 키 조합 폭발, 빈번한 무효화, 낮은 hit rate 등 cost-benefit이 불리. 인덱스 최적화(목표 ①)만으로 10만건 수준에서는 DB 부하가 수용 가능할 수 있음. 상세 캐시만 먼저 적용해도 효과적

**6. 가장 먼저 손대야 할 위험 요소**
- **Redis 장애 시 fallback 전략 구현**. 캐시를 도입하는 순간 Redis가 새로운 의존성이 됨. fallback 없이 배포하면 Redis 다운 = 서비스 다운. `try-catch` 기반 graceful degradation을 캐시 적용과 동시에 구현해야 함

---

## 종합 교차 분석

### 세 목표 간 의존 관계

```
① 인덱스 최적화 ←── 독립 (가장 먼저 수행 가능)
     ↑
② 좋아요 비정규화 ── 이미 완료. 인덱스만 추가하면 됨 (①과 병합)
     ↑
③ 캐시 적용 ←── ①②가 선행되어야 캐시 miss 시 안전
```

### 우선순위 제안

| 순서 | 작업 | 이유 |
|------|------|------|
| 1 | ① + ② 병합: 인덱스 추가 + EXPLAIN 분석 | 비정규화는 이미 돼있으므로 인덱스만 추가. 캐시 miss의 안전망 역할 |
| 2 | ③ 상세 캐시 | 키 설계가 단순하고 hit rate 높음 |
| 3 | ③ 목록 캐시 | 키 폭발·무효화 복잡도가 높아 가장 리스크 큼 |

### 체크리스트 대응 현황

| 체크리스트 항목 | 설계 대응 |
|---|---|
| **[Index]** brandId 기반 검색 + 좋아요 순 정렬 처리 | 6가지 유즈케이스 매트릭스 분석 완료. 3종 복합 인덱스 후보 도출 |
| **[Index]** 조회 필터·정렬 조건별 유즈케이스 분석 + 전후 성능비교 | EXPLAIN 분석 계획 수립 (AS-IS/TO-BE 6개 유즈케이스 비교) |
| **[Structure]** 좋아요 수 조회 및 좋아요 순 정렬 가능 구조 | 이미 구현됨. `products.like_count` 비정규화 + `LIKES_DESC` 정렬. 인덱스 추가로 성능 개선 |
| **[Structure]** 좋아요 적용/해제 시 동기화 | 이미 구현됨. 동일 TX 내 원자적 UPDATE (`like_count + 1` / `like_count - 1`) |
| **[Cache]** Redis 캐시 + TTL/무효화 전략 | RedisTemplate 직접 사용. 상세(Facade, TTL 2분) + ID 리스트(Service, TTL 3분). targeted write-through + safety-net TTL |
| **[Cache]** 캐시 미스 시 정상 동작 | Cache-Aside + Redis 장애 시 try-catch fallback to DB. TTL jitter + PER + LocalCacheLock 적용 |

### 가장 큰 구조적 리스크 Top 3

1. **캐시 무효화와 데이터 변경의 원자성 미보장**: 현재 아키텍처에서 `@Transactional` 커밋 후 Redis 무효화를 수행하면, 그 사이에 정합성 gap이 발생. `@TransactionalEventListener(phase = AFTER_COMMIT)` 패턴을 써도 Redis 호출 실패 시 복구 수단이 없음

2. **목록 캐시의 키 폭발 + 좋아요 변경 시 대량 무효화**: 좋아요가 빈번한 서비스에서 `LIKES_DESC` 정렬의 모든 페이지 캐시를 무효화하는 것은 사실상 "캐시를 쓰지 않는 것"과 같아질 수 있음

3. **인덱스 3벌 유지 비용 vs. 쿼리 성능 trade-off**: 정렬 타입 3종(LATEST, PRICE_ASC, LIKES_DESC)에 대해 각각 최적 인덱스를 만들면 INSERT/UPDATE 시 인덱스 유지 비용 증가. `like_count` UPDATE가 빈번하면 `(brand_id, deleted_at, like_count)` 인덱스의 재정렬 비용이 쓰기 성능을 악화시킬 수 있음

---

## 참고자료

### 캐시 TTL 설정 근거

| 출처 | 링크 |
|------|------|
| AWS - Database Caching Strategies Using Redis (Cache Validity) | https://docs.aws.amazon.com/whitepapers/latest/database-caching-strategies-using-redis/cache-validity.html |
| AWS - ElastiCache Caching Strategies | https://docs.aws.amazon.com/AmazonElastiCache/latest/dg/Strategies.html |
| Redis 공식 블로그 - Cache Optimization Strategies | https://redis.io/blog/guide-to-cache-optimization-strategies/ |
| ByteByteGo - A Crash Course in Caching (Final Part) | https://blog.bytebytego.com/p/a-crash-course-in-caching-final-part |
| ByteByteGo - A Guide to Top Caching Strategies | https://blog.bytebytego.com/p/a-guide-to-top-caching-strategies |
| 올리브영 테크블로그 - 고성능 캐시 아키텍처 설계 | https://oliveyoung.tech/2024-12-10/present-promotion-multi-layer-cache/ |
| 카카오페이 기술 블로그 - 분산 시스템에서 로컬 캐시 활용하기 | https://tech.kakaopay.com/post/local-caching-in-distributed-systems/ |

### 캐시 무효화 전략 근거

| 출처 | 링크 |
|------|------|
| Redis 공식 - Cache Invalidation | https://redis.io/glossary/cache-invalidation/ |
| Redis 공식 블로그 - Three Ways to Maintain Cache Consistency | https://redis.io/blog/three-ways-to-maintain-cache-consistency/ |
| AWS Builders Library - Caching Challenges and Strategies | https://aws.amazon.com/builders-library/caching-challenges-and-strategies/ |
| Microsoft Azure - Cache-Aside Pattern | https://learn.microsoft.com/en-us/azure/architecture/patterns/cache-aside |
| Inpa Dev - Redis 캐시 설계 전략 지침 총정리 | https://inpa.tistory.com/entry/REDIS-%F0%9F%93%9A-%EC%BA%90%EC%8B%9CCache-%EC%84%A4%EA%B3%84-%EC%A0%84%EB%9E%B5-%EC%A7%80%EC%B9%A8-%EC%B4%9D%EC%A0%95%EB%A6%AC |
| daily.dev - Cache Invalidation vs. Expiration Best Practices | https://daily.dev/blog/cache-invalidation-vs-expiration-best-practices |
| Toss Tech - Cache Traffic Tips | https://toss.tech/article/cache-traffic-tip |

### 인덱스 및 쿼리 최적화

| 출처 | 링크 |
|------|------|
| 쿼리 튜닝과 인덱스 최적화 (WikiDocs) | https://wikidocs.net/226253 |
| 카카오 테크 - MySQL 방향별 인덱스 | https://tech.kakao.com/posts/351 |

### Spring + Redis 구현

| 출처 | 링크 |
|------|------|
| Spring 공식 - Caching | https://docs.spring.io/spring-boot/reference/io/caching.html |
| Spring Data Redis - Redis Cache Reference | https://docs.spring.io/spring-data/redis/reference/redis/redis-cache.html |
| Baeldung - Spring Data Redis Tutorial | https://www.baeldung.com/spring-data-redis-tutorial |
