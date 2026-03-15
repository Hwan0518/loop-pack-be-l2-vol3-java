# 성능 개선 구현 계획

## Context

상품 목록/상세 조회 API의 성능을 개선한다. 현재 상태:
- `products` 테이블에 PK 외 인덱스 없음 → 10만건 이상에서 Full Table Scan + filesort
- 상품 목록 쿼리가 `products LEFT JOIN brands`로 매번 JOIN 수행
- Redis 캐시 미적용
- 좋아요 수 비정규화(`like_count`)와 동기화는 이미 구현 완료

**3가지 개선 축**:
1. **Read Model** — 조회 전용 테이블(`product_read_model`)로 JOIN 제거
2. **인덱스** — Read Model 테이블에 복합 인덱스 추가
3. **캐시** — 상세/목록 API에 Redis Cache-Aside 적용

**요구사항 체크리스트**:

| # | 항목 | 대응 |
|---|------|------|
| 1 | [Index] brandId 기반 검색 + 좋아요 순 정렬 | Task 1: Read Model 인덱스 |
| 2 | [Index] 유즈케이스별 인덱스 + 전후 성능비교 | Task 1: EXPLAIN AS-IS vs TO-BE |
| 3 | [Structure] 좋아요 수 조회 + 정렬 | **이미 완료** (`products.like_count` + `LIKES_DESC` 정렬) |
| 4 | [Structure] 좋아요 적용/해제 시 동기화 | **이미 완료** (동일 TX 내 원자적 UPDATE) |
| 5 | [Cache] Redis 캐시 + TTL/무효화 전략 | Task 2~3: 상세 + 목록 캐시 |
| 6 | [Cache] 캐시 미스 시 정상 동작 | Task 2: try-catch 장애 격리 |

---

## 비정규화 vs Read Model (Materialized View) 비교

| 관점 | 비정규화 (`like_count`) | Read Model (`product_read_model`) |
|------|----------------------|----------------------------------|
| 구조 | 기존 테이블에 컬럼 추가 | 조회 전용 별도 테이블 |
| 정합성 | 동일 TX 내 원자적 UPDATE → 즉시 일관성 | 동일 TX 내 동기 sync → 즉시 일관성 |
| 조회 성능 | COUNT 서브쿼리/JOIN 제거 → 단일 컬럼 정렬 | JOIN 자체를 제거 → 단일 테이블 SELECT |
| 인덱스 | write 테이블에 인덱스 추가 → 쓰기 부하 증가 | read 전용 테이블에 인덱스 → write 무영향 |
| 확장성 | 비정규화 대상마다 컬럼 추가 필요 | 조회에 필요한 정보를 자유롭게 포함 가능 |
| 복잡도 | 낮음 (컬럼 1개 + UPDATE SQL) | 중간 (테이블 + 동기화 로직) |

**이 프로젝트의 선택**:
- **비정규화**: 좋아요 수(`like_count`) — 단일 집계 값, 동기화가 단순하므로 기존 테이블에 컬럼 추가로 해결
- **Read Model**: 브랜드명(`brand_name`) — 상품 목록/상세에서 매번 `LEFT JOIN brands` 수행. Read Model로 JOIN 제거 + 향후 추가 정보(카테고리명 등) 확장 용이

---

## 결정사항

| 결정 | 선택 | 근거 |
|------|------|------|
| Read Model 방식 | **별도 `product_read_model` 테이블** | JOIN 제거 + write/read 분리 + 확장성 |
| Read Model 동기화 | **동일 TX 내 동기 sync** | 도메인 이벤트 미도입 상태. 실시간 정합성 보장 |
| 인덱스 위치 | **Read Model 테이블에만 적용** | write 테이블 쓰기 성능 유지. 사용자 조회는 read model 경유 |
| 캐시 직렬화 | **StringRedisTemplate + ObjectMapper** | 기존 modules/redis 수정 불필요, 순수 JSON, 디버깅 용이 |
| 상세 캐시 | **적용** (TTL 10분) | 요구사항에 '상품 상세 API에 캐시 적용' 명시 |
| 목록 캐시 | **적용** (TTL 5분) | 요구사항에 '상품 목록 API에 캐시 적용' 명시 |
| 목록 캐시 무효화 | **SCAN + TTL 병행** | Active invalidation + safety-net TTL |
| 관리자 API 캐시 | **미적용** | 관리자는 실시간 데이터 필요 |

---

## 아키텍처 제약사항 (ArchUnit)

`LayerDependencyArchTest`의 관련 규칙:

| 규칙 | 영향 |
|------|------|
| Facade → Infrastructure 금지 | Facade에서 CacheManager 직접 사용 불가 |
| Facade → Port **인터페이스** 금지 | Facade에서 CachePort 인터페이스도 사용 불가 |
| Service → Service 금지 | Service 간 직접 호출 불가 |
| **Service → Infrastructure** | **금지 규칙 없음** |

**핵심**: Service → Infrastructure를 차단하는 ArchUnit 규칙이 없다. 따라서 Port 인터페이스 없이 Service에서 `ProductCacheManager`(infrastructure)를 직접 사용 가능하다.

**캐시 배치 전략**:
- **목록 캐시**: `ProductQueryService`에서 Cache-Aside 처리 (Service 내부, Facade 무관)
- **상세 캐시**: `ProductQueryFacade`가 Service의 캐시 read/write 메서드를 호출하여 오케스트레이션 (상세 DTO = Product + BrandName 2개 도메인 조합이므로 Facade 레벨에서 처리)
- **캐시 무효화**: `ProductCommandService`에서 mutation 후 eviction 처리

---

## Task 분해 및 실행 순서

```
Phase 1 (병렬):  [Task 1: Read Model + 인덱스] || [Task 2: 캐시 인프라]
                  ↓ 파일 충돌 없음
Phase 2:         [Task 3: 캐시 적용 + Read Model 쿼리 전환]
                  ↓
Phase 3:         [QA: 전체 테스트 검증 (ArchUnit 포함)]
```

**병렬 실행 근거 (Phase 1)**:
- Task 1: `ProductReadModelEntity` + `ProductReadModelRepository` + DDL + EXPLAIN 테스트
- Task 2: `ProductCacheManager` + 테스트
- 파일 겹침 없음

---

## Task 1: Read Model + 인덱스

**목표**: 조회 전용 `product_read_model` 테이블 생성 + 복합 인덱스 추가 + 동기화 + 쿼리 전환 + EXPLAIN 전후 비교
**체크리스트 대응**: [Index] 1, 2번 + [Structure] Read Model

### 1-1. Read Model 테이블 설계

```sql
CREATE TABLE product_read_model (
    id              BIGINT PRIMARY KEY,          -- products.id와 동일 (FK 아님, 동기화로 관리)
    brand_id        BIGINT       NOT NULL,
    brand_name      VARCHAR(100),                -- brands.name 비정규화
    name            VARCHAR(200) NOT NULL,       -- 상품명
    price           DECIMAL(12,2) NOT NULL,
    stock           BIGINT       NOT NULL,
    description     VARCHAR(1000),               -- nullable
    like_count      BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP    NOT NULL,
    deleted_at      TIMESTAMP,                   -- soft delete

    -- 복합 인덱스: 카디널리티 높은 brand_id 선두 + deleted_at 필터 + 정렬
    INDEX idx_read_brand_deleted_created   (brand_id, deleted_at, created_at),
    INDEX idx_read_brand_deleted_price     (brand_id, deleted_at, price),
    INDEX idx_read_brand_deleted_likecount (brand_id, deleted_at, like_count)
);
```

> **인덱스를 Read Model에만 적용하는 이유**: `products` 원본 테이블은 쓰기 전용. 인덱스를 원본에 추가하면 INSERT/UPDATE 시 인덱스 유지 비용 발생. Read Model은 조회 전용이므로 인덱스를 자유롭게 추가 가능.

### 1-2. 신규 파일 — Entity

**`apps/.../catalog/product/infrastructure/entity/ProductReadModelEntity.java`**

```java
@Entity
@Table(name = "product_read_model", indexes = {
    @Index(name = "idx_read_brand_deleted_created", columnList = "brand_id, deleted_at, created_at"),
    @Index(name = "idx_read_brand_deleted_price", columnList = "brand_id, deleted_at, price"),
    @Index(name = "idx_read_brand_deleted_likecount", columnList = "brand_id, deleted_at, like_count")
})
public class ProductReadModelEntity {
    @Id private Long id;                    // products.id (AUTO_INCREMENT 아님, 직접 설정)
    private Long brandId;
    private String brandName;               // 비정규화
    private String name;
    @Column(precision = 12, scale = 2)
    private BigDecimal price;
    private Long stock;
    @Column(length = 1000)
    private String description;
    private Long likeCount;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;
    private ZonedDateTime deletedAt;

    // of(Product, brandName): 정적 팩토리
}
```

### 1-3. 신규 파일 — JPA Repository

**`apps/.../catalog/product/infrastructure/jpa/ProductReadModelJpaRepository.java`**

```java
public interface ProductReadModelJpaRepository extends JpaRepository<ProductReadModelEntity, Long> {
    @Modifying @Query("UPDATE ProductReadModelEntity e SET e.brandName = :brandName WHERE e.brandId = :brandId")
    void updateBrandNameByBrandId(@Param("brandId") Long brandId, @Param("brandName") String brandName);

    @Modifying @Query("UPDATE ProductReadModelEntity e SET e.likeCount = e.likeCount + 1 WHERE e.id = :id")
    void increaseLikeCount(@Param("id") Long id);

    @Modifying @Query("UPDATE ProductReadModelEntity e SET e.likeCount = e.likeCount - 1 WHERE e.id = :id AND e.likeCount > 0")
    void decreaseLikeCount(@Param("id") Long id);
}
```

### 1-4. 신규 파일 — Domain Repository Interface + Implementation

**`apps/.../catalog/product/domain/repository/ProductReadModelRepository.java`**

```java
/**
 * 상품 Read Model 동기화 리포지토리
 * - write 경로에서 product_read_model 테이블을 동기화
 * - 구현체: ProductReadModelRepositoryImpl (infrastructure/repository/)
 */
public interface ProductReadModelRepository {
    void save(Product product, String brandName);
    void delete(Long productId);
    void increaseLikeCount(Long productId);
    void decreaseLikeCount(Long productId);
    void updateStock(Long productId, Long newStock);
    void updateBrandName(Long brandId, String newBrandName);
}
```

**`apps/.../catalog/product/infrastructure/repository/ProductReadModelRepositoryImpl.java`**

```java
@Repository
@RequiredArgsConstructor
public class ProductReadModelRepositoryImpl implements ProductReadModelRepository {
    private final ProductReadModelJpaRepository jpaRepository;
    // 각 메서드: Entity 변환 후 JPA 호출
}
```

### 1-5. 수정 파일 — 동기화 배선

**`ProductCommandService.java`** — `ProductReadModelRepository` 의존성 추가:

| 메서드 | Read Model 동기화 |
|--------|------------------|
| `createProduct()` | Facade에서 brandName과 함께 `syncReadModel()` 호출 |
| `updateProduct()` | Facade에서 brandName과 함께 `syncReadModel()` 호출 |
| `deleteProduct()` | `readModelRepository.delete(productId)` |
| `increaseLikeCount()` | `readModelRepository.increaseLikeCount(productId)` |
| `decreaseLikeCount()` | `readModelRepository.decreaseLikeCount(productId)` |
| `decreaseStock()` | `readModelRepository.updateStock(productId, newStock)` |

```java
// ProductCommandService — 신규 메서드
// 7. Read Model 동기화 (상품 생성/수정 시 Facade에서 호출)
@Transactional
public void syncReadModel(Product product, String brandName) {
    readModelRepository.save(product, brandName);
}

// 8. Read Model 브랜드명 일괄 동기화 (브랜드 수정 시 BrandCommandFacade에서 호출)
@Transactional
public void syncBrandNameInReadModel(Long brandId, String brandName) {
    readModelRepository.updateBrandName(brandId, brandName);
}
```

**`ProductCommandFacade.java`** — create/update 시 Read Model sync 추가:

```java
// 1. 상품 생성
@Transactional
public AdminProductDetailOutDto createProduct(AdminProductCreateInDto inDto) {
    Brand brand = brandQueryService.getBrandById(inDto.brandId());
    Product savedProduct = productCommandService.createProduct(inDto);
    // Read Model 동기화
    productCommandService.syncReadModel(savedProduct, brand.getName().value());
    return AdminProductDetailOutDto.from(savedProduct, brand.getName().value());
}

// 2. 상품 수정
@Transactional
public AdminProductDetailOutDto updateProduct(Long id, AdminProductUpdateInDto inDto) {
    Product product = productQueryService.findActiveById(id);
    Product updatedProduct = productCommandService.updateProduct(product, inDto);
    Brand brand = brandQueryService.getBrandById(updatedProduct.getBrandId());
    // Read Model 동기화
    productCommandService.syncReadModel(updatedProduct, brand.getName().value());
    return AdminProductDetailOutDto.from(updatedProduct, brand.getName().value());
}
```

**`BrandCommandFacade.java`** — 브랜드명 변경 시 Read Model 동기화:

```java
// 기존 의존성에 추가
private final ProductCommandService productCommandService;  // 같은 BC (catalog)

// updateBrand() 수정
@Transactional
public AdminBrandDetailOutDto updateBrand(Long id, AdminBrandUpdateInDto inDto) {
    Brand brand = brandQueryService.getBrandById(id);
    Brand updatedBrand = brandCommandService.updateBrand(brand, inDto);
    // 상품 Read Model의 brand_name 일괄 동기화
    productCommandService.syncBrandNameInReadModel(id, updatedBrand.getName().value());
    return AdminBrandDetailOutDto.from(updatedBrand);
}
```

### 1-6. 수정 파일 — 쿼리 전환

**`ProductQuerydslRepository.java`** — `product_read_model` 테이블에서 조회하도록 변경:

```java
// AS-IS: products LEFT JOIN brands
QProductEntity product = QProductEntity.productEntity;
QBrandEntity brand = QBrandEntity.brandEntity;
query.from(product).leftJoin(brand).on(brand.id.eq(product.brandId))

// TO-BE: product_read_model (JOIN 없음)
QProductReadModelEntity readModel = QProductReadModelEntity.productReadModelEntity;
query.from(readModel)
    .where(readModel.deletedAt.isNull())
    .select(Projections.constructor(ProductOutDto.class,
        readModel.id, readModel.brandId, readModel.brandName,
        readModel.name, readModel.price, readModel.stock, readModel.likeCount))
```

- JOIN 제거 → 단일 테이블 SELECT
- `brand.name` → `readModel.brandName` (비정규화 컬럼)
- 관리자 쿼리(`searchAdminProducts`)도 동일하게 Read Model에서 조회

### 1-7. EXPLAIN 전후 비교 테스트

**`src/benchmark/.../infrastructure/ProductIndexPerformanceTest.java`**

```java
@SpringBootTest
@ActiveProfiles("test")  // ddl-auto: create → Read Model 인덱스 자동 생성
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
class ProductIndexPerformanceTest {

    @Autowired EntityManager entityManager;
    @Autowired DatabaseCleanUp databaseCleanUp;

    @AfterEach void tearDown() { databaseCleanUp.truncateAllTables(); }

    // @BeforeEach: 50개 브랜드 + 100,000개 상품 + Read Model bulk insert
}
```

**EXPLAIN 비교 절차**:

```
1. 데이터 준비
   - brands: 50개
   - products: 100,000개 (브랜드별 랜덤 분포, 가격/좋아요 랜덤)
   - product_read_model: products + brand_name JOIN 결과 INSERT

2. AS-IS 측정 (products LEFT JOIN brands, 인덱스 없음)
   - DROP INDEX on products (PK 외 인덱스 없는 상태 확인)
   - 6개 유즈케이스별 EXPLAIN ANALYZE 실행
   - 결과 로깅: type, rows, Extra, actual time

3. TO-BE 측정 (product_read_model, 인덱스 있음)
   - Read Model 테이블에 @Table 인덱스 자동 생성됨
   - 6개 유즈케이스별 EXPLAIN ANALYZE 실행 (JOIN 없는 단일 테이블 쿼리)
   - 결과 로깅: type, rows, Extra, actual time

4. 전후 비교 출력
```

**6개 유즈케이스**:

| # | brandId | 정렬 | AS-IS (예상) | TO-BE (목표) |
|---|:---:|---|---|---|
| 1 | X | LATEST | Full Scan + JOIN + filesort | range scan, filesort 확인 필요 |
| 2 | X | PRICE_ASC | Full Scan + JOIN + filesort | range scan, filesort 확인 필요 |
| 3 | X | LIKES_DESC | Full Scan + JOIN + filesort | range scan, filesort 확인 필요 |
| 4 | O | LATEST | Full Scan + JOIN + filesort | ref/range, filesort 없음 |
| 5 | O | PRICE_ASC | Full Scan + JOIN + filesort | ref/range, filesort 없음 |
| 6 | O | LIKES_DESC | Full Scan + JOIN + filesort | ref/range, filesort 없음 |

> **no-brand 쿼리(#1,#2,#3)**: 인덱스 `(brand_id, deleted_at, sort_col)`에서 선두 컬럼 `brand_id`가 쿼리에 없으므로 인덱스 활용 불가 → 별도 2-column 인덱스 `(deleted_at, sort_col)` 필요.
>
> **컬럼 순서 원칙**: 카디널리티가 높은 `brand_id`(수십~수백)를 `deleted_at`(2값: NULL/timestamp)보다 앞에 배치. 두 컬럼 모두 equality 조건이므로 인덱스 탐색 결과는 동일하나, B-tree fan-out이 더 균등해져 인덱스 효율이 향상됨.

### 1-8. DDL Migration 스크립트

**`round5-docs/migration/V5__add_product_read_model.sql`**

```sql
-- Read Model 테이블 생성
CREATE TABLE product_read_model (
    id              BIGINT PRIMARY KEY,
    brand_id        BIGINT       NOT NULL,
    brand_name      VARCHAR(100),
    name            VARCHAR(200) NOT NULL,
    price           DECIMAL(12,2) NOT NULL,
    stock           BIGINT       NOT NULL,
    description     VARCHAR(1000),
    like_count      BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP    NOT NULL,
    deleted_at      TIMESTAMP
);

-- 복합 인덱스 (카디널리티 높은 brand_id 선두)
CREATE INDEX idx_read_brand_deleted_created
    ON product_read_model (brand_id, deleted_at, created_at);
CREATE INDEX idx_read_brand_deleted_price
    ON product_read_model (brand_id, deleted_at, price);
CREATE INDEX idx_read_brand_deleted_likecount
    ON product_read_model (brand_id, deleted_at, like_count);

-- 초기 데이터 마이그레이션
INSERT INTO product_read_model (id, brand_id, brand_name, name, price, stock, description, like_count, created_at, updated_at, deleted_at)
SELECT p.id, p.brand_id, b.name, p.name, p.price, p.stock, p.description, p.like_count, p.created_at, p.updated_at, p.deleted_at
FROM products p
LEFT JOIN brands b ON b.id = p.brand_id;
```

---

## Task 2: 캐시 인프라 (ProductCacheManager + CacheLock)

**목표**: Redis 캐시 유틸리티 컴포넌트 + 캐시 스탬피드 보호. Redis 장애 격리 + TTL jitter + PER + 로컬 락
**체크리스트 대응**: [Cache] 5, 6번 기반

### 2-1. 설계 결정

| 결정 | 근거 |
|------|------|
| `RedisTemplate<String, String>` + `ObjectMapper` | 기존 modules/redis 수정 불필요, 순수 JSON |
| 읽기: `defaultRedisTemplate` (REPLICA_PREFERRED) | master-replica 토폴로지 활용 |
| 쓰기/삭제: `masterRedisTemplate` (`@Qualifier REDIS_TEMPLATE_MASTER`) | 데이터 정합성 |
| TTL jitter: `TTL + random(0, TTL * 0.1)` | multi-key 동시 만료 방어 (thundering herd) |
| 모든 메서드 try-catch | Redis 장애 → 서비스 장애 전파 차단 |
| **Port 인터페이스 없음** | ArchUnit에 Service → Infrastructure 차단 규칙 없음. 과제 예시도 Service에서 RedisTemplate 직접 사용 |
| **CacheLock 인터페이스** + `LocalCacheLock`(`@Primary`) | single-key 스탬피드 방어. 향후 분산 환경 전환 시 `RedisCacheLock`으로 교체 가능 |
| **PER (Probabilistic Early Refresh)** | 캐시 만료 자체를 예방. TTL 임박 시 확률적으로 미리 갱신 |

### 2-1-1. 캐시 스탬피드 보호 전략 (3계층)

```
[요청] → 캐시 조회
           │
     ┌─────▼──────────────┐
     │ 히트 + TTL 여유      │──→ 바로 반환
     │ 히트 + TTL 임박      │──→ 반환 + 비동기 갱신 ← (C: PER — 만료 예방)
     │ 미스                │──→ key-level 로컬 락  ← (B: Local Mutex — 중복 조회 방지)
     │ Redis 장애          │──→ DB 직행 (try-catch) ← 장애 격리
     └────────────────────┘
```

| 계층 | 방어 대상 | 구현 |
|------|-----------|------|
| **TTL jitter** | multi-key 동시 만료 | TTL + random(0, TTL * 0.1) |
| **PER** | single-key 만료 자체를 예방 | TTL 남은 시간 < threshold 시 확률적 백그라운드 갱신 |
| **Local Mutex** | 만료 후 DB 중복 조회 (1개 key에 100명 동시 miss) | `CacheLock` interface + `LocalCacheLock`(`@Primary`) |
| **try-catch** | Redis 장애 시 서비스 정상 동작 | 모든 캐시 메서드 예외 격리 → DB fallback |
| **인덱스** | 최종 안전망 | DB 직행해도 Read Model 인덱스로 빠른 응답 |

### 2-2. 신규 파일 — CacheLock (전략 패턴)

**`apps/.../catalog/product/infrastructure/cache/CacheLock.java`** (인터페이스)

```java
/**
 * 캐시 스탬피드 방지용 key-level 락
 * - 같은 key에 대한 동시 DB 조회를 1회로 제한
 * - 구현체: LocalCacheLock (@Primary), RedisCacheLock (분산 환경 전환용)
 */
public interface CacheLock {
    <T> T executeWithLock(String key, Supplier<T> loader);
}
```

**`apps/.../catalog/product/infrastructure/cache/LocalCacheLock.java`** (`@Primary`)

```java
/**
 * JVM 로컬 key-level 캐시 락
 * - ConcurrentHashMap + synchronized로 같은 key 요청만 직렬화
 * - 다른 key 요청은 병렬 처리 (key 단위 세밀한 락)
 * - 단일 서버 환경에서 사용. 분산 환경 전환 시 RedisCacheLock으로 @Primary 이동
 */
@Primary
@Component
public class LocalCacheLock implements CacheLock {
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    @Override
    public <T> T executeWithLock(String key, Supplier<T> loader) {
        Object lock = locks.computeIfAbsent(key, k -> new Object());
        synchronized (lock) {
            try {
                return loader.get();
            } finally {
                locks.remove(key);
            }
        }
    }
}
```

**`apps/.../catalog/product/infrastructure/cache/RedisCacheLock.java`** (분산 환경 전환용)

```java
/**
 * Redis SETNX 기반 분산 캐시 락
 * - 분산 환경(multi-JVM)에서 사용
 * - 현재는 대기 상태. 분산 환경 전환 시 @Primary 이동
 */
@Component
public class RedisCacheLock implements CacheLock {
    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public <T> T executeWithLock(String key, Supplier<T> loader) {
        String lockKey = key + ":lock";
        Boolean acquired = redisTemplate.opsForValue()
            .setIfAbsent(lockKey, "1", Duration.ofSeconds(5));
        try {
            if (Boolean.TRUE.equals(acquired)) {
                return loader.get();
            } else {
                Thread.sleep(50);
                return loader.get();  // 대기 후 재시도 (캐시 히트 기대)
            }
        } finally {
            if (Boolean.TRUE.equals(acquired)) {
                redisTemplate.delete(lockKey);
            }
        }
    }
}
```

### 2-3. 신규 파일 — ProductCacheManager

**`apps/.../catalog/product/infrastructure/cache/ProductCacheManager.java`**

```java
/**
 * 상품 캐시 관리자
 * - Redis 기반 Cache-Aside 패턴 지원
 * - 모든 메서드는 Redis 장애 시 예외를 격리하고 로깅만 수행
 * - 읽기: replica-preferred, 쓰기/삭제: master
 * - 캐시 스탬피드 보호: CacheLock + PER (Probabilistic Early Refresh)
 *
 * 1. get(key, Class) — 단순 타입 캐시 조회
 * 2. get(key, TypeReference) — 제네릭 타입 캐시 조회
 * 3. put(key, value, ttl) — 캐시 저장 (TTL jitter 포함)
 * 4. evict(key) — 단일 키 삭제
 * 5. evictByPattern(pattern) — SCAN 기반 패턴 삭제
 * 6. getOrLoad(key, type, ttl, loader) — Cache-Aside + 스탬피드 보호 (CacheLock + double-check)
 * 7. getOrLoadWithPer(key, type, ttl, loader) — getOrLoad + PER (TTL 임박 시 확률적 갱신)
 */
@Slf4j
@Component
public class ProductCacheManager {

    private final RedisTemplate<String, String> readTemplate;   // default (replica-preferred)
    private final RedisTemplate<String, String> writeTemplate;  // master
    private final ObjectMapper objectMapper;
    private final CacheLock cacheLock;

    // --- 기본 메서드 ---
    // get(): Redis 조회 실패 시 Optional.empty() 반환 → DB fallback
    // put(): TTL에 jitter(0~10%) 추가하여 동시 만료 방지
    // evict(): 단일 키 삭제. 실패 시 무시 → TTL 만료에 의존
    // evictByPattern(): SCAN 기반 non-blocking 패턴 삭제

    // --- 스탬피드 보호 메서드 ---

    /**
     * 6. Cache-Aside + 스탬피드 보호
     * - CacheLock으로 같은 key에 대한 동시 DB 조회를 1회로 제한
     * - double-check: 락 대기 후 캐시 재조회 (다른 스레드가 저장했을 수 있음)
     */
    public <T> T getOrLoad(String key, Class<T> type, Duration ttl, Supplier<T> loader) {
        // 캐시 조회
        Optional<T> cached = get(key, type);
        if (cached.isPresent()) return cached.get();

        // 캐시 미스 → 락 획득 후 DB 조회 (1회만)
        return cacheLock.executeWithLock(key, () -> {
            // double-check (대기 중 다른 스레드가 캐시 저장했을 수 있음)
            Optional<T> doubleCheck = get(key, type);
            if (doubleCheck.isPresent()) return doubleCheck.get();

            // DB 조회 + 캐시 저장
            T value = loader.get();
            put(key, value, ttl);
            return value;
        });
    }

    /**
     * 7. Cache-Aside + PER (Probabilistic Early Refresh) + 스탬피드 보호
     * - TTL 잔여 시간이 threshold 이하이면 확률적으로 갱신 → 캐시 만료 자체를 예방
     * - PER을 뚫고 만료 발생 시 CacheLock이 DB 중복 조회 방지
     */
    public <T> T getOrLoadWithPer(String key, Class<T> type, Duration ttl, Supplier<T> loader) {
        Optional<T> cached = get(key, type);
        if (cached.isPresent()) {
            // PER: TTL 잔여 시간 확인 → 임박 시 확률적 갱신
            if (shouldEarlyRefresh(key, ttl)) {
                CompletableFuture.runAsync(() -> {
                    T fresh = loader.get();
                    put(key, fresh, ttl);
                });
            }
            return cached.get();
        }

        // 캐시 미스 → 락 + double-check
        return cacheLock.executeWithLock(key, () -> {
            Optional<T> doubleCheck = get(key, type);
            if (doubleCheck.isPresent()) return doubleCheck.get();

            T value = loader.get();
            put(key, value, ttl);
            return value;
        });
    }

    /**
     * PER 판정: TTL의 마지막 20% 구간에서 확률적 갱신
     * - 남은 시간이 적을수록 갱신 확률 증가
     * - 예: TTL 10분, 남은 시간 1분 → 갱신 확률 ~50%
     */
    private boolean shouldEarlyRefresh(String key, Duration baseTtl) {
        try {
            Long remainMs = readTemplate.getExpire(key, TimeUnit.MILLISECONDS);
            if (remainMs == null || remainMs <= 0) return false;

            long thresholdMs = baseTtl.toMillis() / 5;  // 20% 구간
            if (remainMs > thresholdMs) return false;

            // 남은 시간이 적을수록 확률 증가 (선형)
            double probability = 1.0 - ((double) remainMs / thresholdMs);
            return ThreadLocalRandom.current().nextDouble() < probability;
        } catch (Exception e) {
            return false;
        }
    }
}
```

### 2-4. 테스트

**`src/test/.../infrastructure/cache/ProductCacheManagerTest.java`** (통합 테스트):
1. put → get(Class): ProductDetailOutDto 직렬화/역직렬화
2. put → get(TypeReference): ProductPageOutDto 제네릭 타입 검증
3. BigDecimal 정밀도: `compareTo` 기준 검증
4. evict: 저장 후 삭제 → Optional.empty()
5. evictByPattern: `products:list:*` 패턴으로 여러 키 일괄 삭제
6. TTL: base ± 10% 범위 검증
7. 존재하지 않는 키 조회 → Optional.empty() (예외 없음)
8. null description 필드 → 직렬화/역직렬화 정상 동작
9. getOrLoad: 캐시 미스 → loader 1회 호출 + 캐시 저장
10. getOrLoad: 캐시 히트 → loader 미호출

**`src/test/.../infrastructure/cache/CacheStampedeTest.java`** (스탬피드 통합 테스트):
1. **single-key 스탬피드**: 캐시 만료 후 100 concurrent 요청 → loader 호출 횟수 검증 (이상: 1회)
2. **multi-key 스탬피드**: TTL jitter 적용된 100개 키 → 동시 만료 분산 검증
3. **PER 동작**: TTL 임박 시 백그라운드 갱신 발생 → 후속 요청은 갱신된 캐시 히트
4. **Redis 장애 시**: 모든 요청이 DB fallback → 서비스 정상 동작 (loader 매번 호출)

**`src/test/.../infrastructure/cache/LocalCacheLockTest.java`** (단위 테스트):
1. 같은 key 100 concurrent → loader 1회만 실행, 나머지는 대기 후 결과 공유
2. 다른 key → 병렬 실행 (서로 블로킹하지 않음)
3. loader 예외 → 락 정상 해제, 예외 전파

---

## Task 3: 캐시 적용 (상세 + 목록 + 무효화)

**목표**: 상세/목록 API에 Cache-Aside 패턴 적용 + 모든 변경 시 캐시 무효화
**체크리스트 대응**: [Cache] 5, 6번

### 3-1. 상세 캐시 설계

| 항목 | 값 |
|------|---|
| 캐시 키 | `product:{productId}` |
| 캐시 레이어 | **Facade 오케스트레이션** (상세 DTO = Product + BrandName 2개 도메인 조합) |
| 캐시 값 | `ProductDetailOutDto` (JSON) |
| TTL | 10분 + jitter |
| 무효화 | 상품 변경/삭제/좋아요 변경 시 `product:{id}` 삭제 + TTL 안전망 |

**Facade 오케스트레이션 방식 (ArchUnit 준수)**:

Facade는 캐시 인프라에 직접 접근할 수 없으므로, Service의 `getOrLoadProductDetail()` 메서드를 호출한다.
Service 내부에서 `ProductCacheManager.getOrLoadWithPer()`를 사용하여 캐시 조회 + 스탬피드 보호 + PER을 일괄 처리:

```java
// ProductQueryService — 상세 캐시 메서드 (PER + 스탬피드 보호 포함)
public ProductDetailOutDto getOrLoadProductDetail(Long productId, Supplier<ProductDetailOutDto> loader) {
    String cacheKey = "product:" + productId;
    return productCacheManager.getOrLoadWithPer(cacheKey, ProductDetailOutDto.class, Duration.ofMinutes(10), loader);
}
```

```java
// ProductQueryFacade — cache-aside 오케스트레이션
@Transactional(readOnly = true)
public ProductDetailOutDto getProduct(Long id) {
    return productQueryService.getOrLoadProductDetail(id, () -> {
        // 캐시 미스 시 DB 조회 (loader)
        Product product = productQueryService.findActiveById(id);
        Brand brand = brandQueryService.getBrandById(product.getBrandId());
        return ProductDetailOutDto.from(product, brand.getName().value());
    });
}
```

- 캐시 히트 + TTL 여유: DB 호출 0회, 바로 반환
- 캐시 히트 + TTL 임박: 반환 + 비동기 갱신 (PER)
- 캐시 미스: 로컬 락으로 1명만 DB 조회, 나머지 대기 후 캐시 히트 (스탬피드 보호)
- Redis 장애: try-catch → loader 실행 → DB 직행 (서비스 정상 동작)
- Facade는 Service 메서드만 호출 → ArchUnit 통과

### 3-2. 목록 캐시 설계

| 항목 | 값 |
|------|---|
| 캐시 키 | `products:list:{brandId\|all}:{sortType\|LATEST}:{page}:{size}` |
| 캐시 레이어 | **Service 내부** (단일 도메인 QueryPort 조회, Facade는 단순 위임) |
| 캐시 값 | `ProductPageOutDto` (JSON) |
| TTL | 5분 + jitter |
| 무효화 | SCAN 기반 `products:list:*` 패턴 삭제 + TTL 안전망 |
| 관리자 검색 | **캐시 미적용** |

```java
// ProductQueryService — searchProducts() 캐시 적용 (PER + 스탬피드 보호)
@Transactional(readOnly = true)
public ProductPageOutDto searchProducts(Long brandId, ProductSortType sortType, int page, int size) {
    String cacheKey = buildListCacheKey(brandId, sortType, page, size);

    return productCacheManager.getOrLoadWithPer(cacheKey, ProductPageOutDto.class, Duration.ofMinutes(5), () -> {
        // 캐시 미스 시 DB 조회 (loader)
        ProductSearchCriteria criteria = new ProductSearchCriteria(brandId, sortType);
        PageResult<ProductOutDto> result = productQueryPort.searchProducts(criteria, new PageCriteria(page, size));
        return ProductPageOutDto.from(result);
    });
}

// searchAdminProducts(): 변경 없음 (캐시 미적용)

private String buildListCacheKey(Long brandId, ProductSortType sortType, int page, int size) {
    String brandKey = brandId != null ? String.valueOf(brandId) : "all";
    String sortKey = sortType != null ? sortType.name() : "LATEST";
    return String.format("products:list:%s:%s:%d:%d", brandKey, sortKey, page, size);
}
```

### 3-3. 캐시 무효화

**`ProductCommandService.java`** — `ProductCacheManager` 의존성 추가, 각 mutation 메서드 끝에 캐시 무효화:

| 메서드 | 상세 캐시 | 목록 캐시 |
|--------|:---------:|:---------:|
| `createProduct()` | - (신규 상품) | `evictByPattern("products:list:*")` |
| `updateProduct()` | Facade에서 처리 (아래 참고) | `evictByPattern("products:list:*")` |
| `deleteProduct()` | Facade에서 처리 (아래 참고) | `evictByPattern("products:list:*")` |
| `decreaseStock()` | `evict("product:" + id)` | `evictByPattern("products:list:*")` |
| `increaseLikeCount()` | `evict("product:" + id)` | `evictByPattern("products:list:*")` |
| `decreaseLikeCount()` | `evict("product:" + id)` | `evictByPattern("products:list:*")` |

> `decreaseStock()` 포함 근거: `ProductOutDto`에 `stock` 필드 포함 → 재고 변경 시 캐시 무효화 필요

**상세 캐시 무효화 — Facade에서 처리하는 메서드**:

`updateProduct()`과 `deleteProduct()`은 Facade에서 `productId`를 알고 있으므로 Service의 evict 메서드 호출:

```java
// ProductCommandService — 캐시 evict 메서드 노출
public void evictProductDetailCache(Long productId) {
    productCacheManager.evict("product:" + productId);
}

// ProductCommandFacade.updateProduct()
Product updatedProduct = productCommandService.updateProduct(product, inDto);
productCommandService.syncReadModel(updatedProduct, brand.getName().value());
productCommandService.evictProductDetailCache(id);  // 상세 캐시 무효화

// ProductCommandFacade.deleteProduct()
productCommandService.deleteProduct(product);
productCommandService.evictProductDetailCache(id);  // 상세 캐시 무효화
```

### 3-4. 테스트

**`ProductQueryServiceTest.java` 수정**:
- `@Mock ProductCacheManager productCacheManager` 추가, 생성자 주입 업데이트
- 신규: `[searchProducts()] 캐시 히트 → 캐시된 ProductPageOutDto 반환. QueryPort 미호출`
- 신규: `[searchProducts()] 캐시 미스 → QueryPort 호출 후 캐시 저장. ProductPageOutDto 반환`
- 신규: `[searchProducts()] brandId=null → 캐시 키에 "all" 사용`
- 기존 `searchAdminProducts`: 캐시 미사용 verify
- 신규: `[getProductDetailCache()] 캐시 히트 → Optional.of(dto) 반환`
- 신규: `[getProductDetailCache()] 캐시 미스 → Optional.empty() 반환`

**`ProductCommandServiceTest.java` 수정**:
- `@Mock ProductCacheManager productCacheManager` + `@Mock ProductReadModelRepository readModelRepository` 추가
- 기존 mutation 테스트에 캐시 eviction verify 추가
- 신규: `[syncReadModel()] Read Model 저장 호출 검증`
- 신규: `[syncBrandNameInReadModel()] 브랜드명 일괄 업데이트 호출 검증`

**`ProductQueryFacadeTest.java` 수정**:
- 신규: `[getProduct()] 캐시 히트 → 캐시된 ProductDetailOutDto 반환. Service/Brand DB 미호출`
- 신규: `[getProduct()] 캐시 미스 → DB 조회 후 캐시 저장. ProductDetailOutDto 반환`

**`ProductCommandFacadeTest.java` 수정**:
- 신규: `[createProduct()] 상품 생성 후 Read Model 동기화 호출 검증`
- 신규: `[updateProduct()] 상품 수정 후 Read Model 동기화 + 상세 캐시 무효화 호출 검증`
- 신규: `[deleteProduct()] 상품 삭제 후 상세 캐시 무효화 호출 검증`

**`BrandCommandFacadeTest.java` 수정**:
- 신규: `[updateBrand()] 브랜드 수정 후 Read Model 브랜드명 동기화 호출 검증`

**`ProductControllerE2ETest.java` 수정**:
- `@Autowired RedisCleanUp redisCleanUp` + `@AfterEach`에 `redisCleanUp.truncateAll()` 추가
- 신규: 상품 수정 후 상세 조회 → 수정된 데이터 반환 (상세 캐시 무효화 검증)
- 신규: 상품 수정 후 목록 조회 → 수정된 데이터 반환 (목록 캐시 무효화 검증)

---

## 크로스-BC 캐시 + Read Model 무효화 흐름

```
ProductLikeCommandFacade.createLike() [engagement BC]
  → ProductLikeCommandService.increaseLikeCount()
    → ProductLikeCountSyncerImpl [ACL — engagement → catalog]
      → ProductCommandFacade.increaseLikeCount() [catalog BC]
        → ProductCommandService.increaseLikeCount()
          → productCommandRepository.increaseLikeCount(productId)     ← products 원본
          → readModelRepository.increaseLikeCount(productId)          ← Read Model 동기화
          → productCacheManager.evict("product:" + productId)         ← 상세 캐시 무효화
          → productCacheManager.evictByPattern("products:list:*")     ← 목록 캐시 무효화
```

기존 ACL 구조 그대로 활용. engagement BC 코드 수정 불필요.

---

## 핵심 파일 목록

### 신규 생성
| 파일 | Task | 설명 |
|------|:----:|------|
| `infrastructure/entity/ProductReadModelEntity.java` | 1 | Read Model JPA 엔티티 |
| `infrastructure/jpa/ProductReadModelJpaRepository.java` | 1 | Read Model Spring Data JPA |
| `domain/repository/ProductReadModelRepository.java` | 1 | Read Model 동기화 인터페이스 |
| `infrastructure/repository/ProductReadModelRepositoryImpl.java` | 1 | Read Model 동기화 구현체 |
| `infrastructure/cache/CacheLock.java` | 2 | 캐시 락 인터페이스 (전략 패턴) |
| `infrastructure/cache/LocalCacheLock.java` | 2 | JVM 로컬 락 구현체 (`@Primary`) |
| `infrastructure/cache/RedisCacheLock.java` | 2 | Redis 분산 락 구현체 (대기) |
| `infrastructure/cache/ProductCacheManager.java` | 2 | Redis 캐시 관리자 (getOrLoad + PER) |
| `test/.../infrastructure/cache/ProductCacheManagerTest.java` | 2 | 캐시 인프라 통합 테스트 |
| `test/.../infrastructure/cache/CacheStampedeTest.java` | 2 | 캐시 스탬피드 통합 테스트 |
| `test/.../infrastructure/cache/LocalCacheLockTest.java` | 2 | 로컬 락 단위 테스트 |
| `test/.../infrastructure/ProductIndexPerformanceTest.java` | 1 | EXPLAIN 전후 비교 테스트 |
| `round5-docs/migration/V5__add_product_read_model.sql` | 1 | DDL + 초기 데이터 migration |

### 수정 대상
| 파일 | Task | 변경 내용 |
|------|:----:|----------|
| `ProductCommandService.java` | 1, 3 | ReadModelRepository + CacheManager 의존성 추가, 동기화/무효화 |
| `ProductCommandFacade.java` | 1, 3 | create/update 시 syncReadModel + evictDetailCache 호출 |
| `ProductQueryService.java` | 3 | CacheManager 의존성 추가, 캐시 유틸 메서드 + 목록 cache-aside |
| `ProductQueryFacade.java` | 3 | 상세 캐시 cache-aside 오케스트레이션 |
| `ProductQuerydslRepository.java` | 1 | Read Model 테이블에서 조회 (JOIN 제거) |
| `BrandCommandFacade.java` | 1 | 브랜드 수정 시 Read Model 브랜드명 동기화 |
| `ProductQueryServiceTest.java` | 3 | Mock 주입 + 캐시 히트/미스 테스트 |
| `ProductCommandServiceTest.java` | 1, 3 | Mock 주입 + Read Model 동기화 + 캐시 무효화 verify |
| `ProductQueryFacadeTest.java` | 3 | 상세 캐시 히트/미스 테스트 |
| `ProductCommandFacadeTest.java` | 1, 3 | Read Model 동기화 + 캐시 무효화 verify |
| `BrandCommandFacadeTest.java` | 1 | 브랜드명 Read Model 동기화 verify |
| `ProductControllerE2ETest.java` | 3 | RedisCleanUp + 캐시 무효화 E2E |

---

## 리스크 및 대응

| # | 리스크 | 심각도 | 대응 |
|---|--------|:------:|------|
| 1 | **no-brand 쿼리 filesort** — 3-column 인덱스에서 `brand_id` skip 시 정렬 인덱스 미활용 | 중 | EXPLAIN 테스트로 확인 후, 필요 시 2-column 인덱스 추가 |
| 2 | **Read Model 동기화 누락** — 새 mutation 추가 시 Read Model sync를 빠뜨리면 데이터 불일치 | 중 | 테스트에서 sync 호출 검증. 향후 도메인 이벤트 전환 시 자동화 가능 |
| 3 | **TX 커밋 전 캐시 무효화** — evict 후 커밋 전 stale 데이터 재캐싱 가능 | 하 | TTL 5~10분이 safety net |
| 4 | **브랜드명 변경 시 캐시 stale** — 목록 캐시에 `brandName` 포함되나 brand 변경 시 캐시 미무효화 | 하 | 브랜드 수정은 관리자 전용 극히 드문 연산. TTL 자연 만료로 충분 |
| 5 | **BigDecimal 직렬화** | 하 | `JacksonConfig`에 `WRITE_BIGDECIMAL_AS_PLAIN` 존재. 테스트로 검증 |
| 6 | **기존 테스트 깨짐** — Service/Facade 생성자에 의존성 추가 | 중 | 각 Task에서 기존 단위테스트 Mock 주입 반드시 업데이트 |
| 7 | **Redis 장애 시 서비스 중단** | 중 | CacheManager 전 메서드 try-catch + Read Model 인덱스가 최종 안전망 |
| 8 | **DDL 미반영** | 중 | DDL migration 스크립트 별도 제공 |
| 9 | **Read Model 저장소 증가** — products 데이터 중복 저장 | 하 | 상품 데이터 크기 자체가 작음. 10만건 기준 수십 MB 수준 |
| 10 | **single-key 캐시 스탬피드** — 인기 상품 캐시 만료 시 동시 DB 조회 | 중 | LocalCacheLock(double-check) + PER(만료 예방) 3계층 방어 |
| 11 | **PER 비동기 갱신 실패** — CompletableFuture 내 예외 | 하 | 갱신 실패해도 기존 캐시 값 정상 반환. 다음 PER 또는 TTL 만료 시 재시도 |

---

## 성능 측정 계획

### 측정 축

| 축 | 값 | 설명 |
|---|---|---|
| **데이터 규모** | 10만 / 100만 / 1000만 | 인덱스 효과가 데이터 규모에 따라 어떻게 변하는지 |
| **트래픽 유형** | 단일 쿼리 / 버스트 / 지속 부하 | 동시성에 따른 성능 변화 |
| **측정 레벨** | DB 쿼리 / API | 인덱스 효과 vs 캐시 효과 분리 측정 |

### 측정 매트릭스

| | 10만 | 100만 | 1000만 |
|---|:---:|:---:|:---:|
| **단일 쿼리** (EXPLAIN + latency) | O | O | O |
| **버스트** (N concurrent) | O | O | O |
| **지속 부하** (N RPS × T초) | O | O | O |

### 측정 레벨별 목적

| 레벨 | 도구 | 측정 대상 | 측정 시점 |
|------|------|-----------|-----------|
| **DB 쿼리 레벨** | `@SpringBootTest` + `DataSource` + `ExecutorService` | EXPLAIN 결과, 순수 쿼리 latency, 인덱스 효과 | AS-IS → TO-BE (인덱스 적용 후) |
| **API 레벨** | `@SpringBootTest` + `MockMvc` + `ExecutorService` | 전체 스택 latency, 캐시 히트/미스 효과, 스탬피드 보호 효과 | AS-IS (기준선) → TO-BE (캐시 적용 후) |

### 병렬 실행

DB 쿼리 레벨과 API 레벨 테스트를 별도 JVM fork에서 동시 실행하여 측정 시간을 단축한다.

```bash
# 병렬 실행 (DB + API 동시, maxParallelForks=2)
./gradlew :apps:commerce-api:benchmarkTest --tests "*PerformanceTest*" -PtestMaxParallelForks=2
```

- `build.gradle.kts`에 `testMaxParallelForks` 프로퍼티 오버라이드 지원 (기본값=1)
- 각 fork는 독립된 TestContainers(MySQL + Redis)를 기동하므로 격리 보장

### 측정 Phase

```
Phase 1: AS-IS (현재 — 인덱스/캐시 없음) ✅ 완료
  ├─ DB 쿼리 레벨: 6 UC × 3 데이터 규모 × 3 트래픽 유형
  └─ API 레벨: 목록 6 UC + 상세 × 3 데이터 규모 × 3 트래픽 유형

Phase 2: TO-BE 인덱스 적용 후 (Read Model + 복합 인덱스)
  └─ DB 쿼리 레벨: 6 UC × 3 데이터 규모 × 3 트래픽 유형

Phase 3: TO-BE 캐시 적용 후
  └─ API 레벨: 상세/목록 API × 3 데이터 규모 × 3 트래픽 유형
     + 캐시 스탬피드 시나리오 (single-key / multi-key)
```

### 트래픽 유형 파라미터

| 유형 | 파라미터 | 측정 방법 |
|------|---------|-----------|
| **단일 쿼리** | 1 thread, 5회 반복 | warmup 3회 + 측정 5회 평균/min/max |
| **버스트** | 100 concurrent threads, `CountDownLatch` 일제 시작 | p50/p95/p99 응답시간, 에러율 |
| **지속 부하** | 20 RPS × 10초 (총 200 요청) | 평균/p50/p95/p99 응답시간, 실제 QPS |

**목표 RPS 설정 근거**: 초기 50 RPS × 30초에서 TestContainers 환경의 커넥션 풀 고갈으로 측정이 불안정해져, 안정적 상대 비교가 가능한 20 RPS × 10초로 조정. 20 RPS는 피크 배수 3x · 피크 4시간 · 사용자당 10~15회 호출 기준으로 **DAU 5~8만 규모** 트래픽에 해당한다. 상세 역산은 `03-as-is-performance-measurement.md` 참고.

### 캐시 스탬피드 측정 (Phase 3 — API 레벨)

| 시나리오 | 설정 | 측정 지표 |
|---------|------|-----------|
| **single-key 스탬피드** | 인기 상품 1개 캐시 만료 → 100 concurrent 요청 | DB 쿼리 횟수 (목표: 1회), p99 응답시간 |
| **multi-key 스탬피드** | 100개 캐시 동시 만료 → 각 키에 요청 | jitter 분산 효과, DB 순간 부하 |
| **PER 효과** | TTL 임박 상태에서 지속 부하 | 캐시 만료 발생 횟수 (PER 미적용 vs 적용) |
| **Redis 장애** | Redis 연결 차단 상태에서 요청 | 서비스 정상 동작 여부, DB fallback 응답시간 |

### 측정 결과 저장 및 시각화

| 파일 | 내용 |
|------|------|
| `round5-docs/03-as-is-performance-measurement.md` | AS-IS 측정 결과 (DB 쿼리 + API 레벨, 전 규모 완료) |
| `round5-docs/04-to-be-index-measurement.md` | TO-BE 인덱스 적용 후 측정 결과 |
| `round5-docs/05-to-be-cache-measurement.md` | TO-BE 캐시 적용 후 측정 결과 + 스탬피드 테스트 결과 |

각 md 파일에 구조화된 테이블로 데이터를 저장한다.

#### 시각화 (Chart.js HTML)

각 Phase 측정 결과를 Chart.js 기반 HTML로 시각화한다. 브라우저에서 열어 인터랙티브하게 확인 가능.

| 파일 | Phase | 내용 |
|------|:-----:|------|
| `round5-docs/as-is-performance-visualization.html` | Phase 1 | AS-IS 측정 결과 시각화 |
| `round5-docs/to-be-index-visualization.html` | Phase 2 | TO-BE 인덱스 적용 후 시각화 |
| `round5-docs/to-be-cache-visualization.html` | Phase 3 | TO-BE 캐시 적용 후 시각화 |
| `round5-docs/06-performance-comparison.html` | 전체 | AS-IS vs TO-BE 전체 비교 그래프 |

**시각화 구성 원칙**:

1. **상단 UC 레퍼런스**: 쿼리 유형별 WHERE/ORDER BY 조건 + 트래픽 유형 정의 테이블을 상단에 배치하여, 차트 라벨만 보고도 실제 쿼리를 파악 가능
2. **상단 KPI 카드**: 핵심 수치(단일 쿼리 응답시간, 에러율, QPS 등)를 6개 이내 카드로 요약
3. **비교 관점(지표) 중심 섹션 구성**: 실험별(A-1, A-2, B-1, ...)이 아닌 비교 지표별로 섹션을 나눠 한눈에 비교
   - 응답시간 비교 (DB vs API 좌우 배치)
   - 에러율 비교 (동일 Y축 0~100%)
   - 처리량 비교 (동일 Y축 + 목표선)
   - DB vs API 오버헤드 비교
4. **차트 라벨**: UC 코드(UC1, UC3) 대신 실제 쿼리 조건 명시 (예: `전체+최신순`, `브랜드+인기순`)
5. **0 값 표시**: 값이 0인 바 위에 "0" 라벨을 표시하여 측정 누락이 아님을 명시 (Chart.js custom plugin)
6. **비교 차트의 축 통일**: 에러율은 0~100%, QPS는 0~max+여유 등 동일 지표의 차트는 Y축 범위를 통일하여 직접 비교 가능

**차트 유형**:
- 데이터 규모별 응답시간 비교 (bar chart)
- AS-IS vs TO-BE 비교 (grouped bar chart)
- 캐시 히트/미스 비교 (line chart)
- 동시 요청 수별 p50/p95/p99 분포 (line chart)
- 스탬피드 보호 전/후 DB 쿼리 횟수 비교 (bar chart)

### 테스트 파일 구조

| 파일 | 레벨 | Phase |
|------|------|-------|
| `ProductIndexPerformanceTest.java` | DB 쿼리 | Phase 1 (AS-IS) + Phase 2 (TO-BE 인덱스) |
| `ProductApiPerformanceTest.java` | API | Phase 1 (AS-IS 기준선) + Phase 3 (TO-BE 캐시) |
| `CacheStampedeTest.java` | API | Phase 3 (스탬피드 시나리오) |

---

## 검증 방법

```bash
# 1. EXPLAIN 전후 비교 (AS-IS vs TO-BE)
./gradlew :apps:commerce-api:benchmarkTest --tests "*ProductIndexPerformanceTest"

# 2. 캐시 인프라 + 스탬피드 테스트
./gradlew :apps:commerce-api:test --tests "*ProductCacheManagerTest"
./gradlew :apps:commerce-api:test --tests "*LocalCacheLockTest"
./gradlew :apps:commerce-api:test --tests "*CacheStampedeTest"

# 3. 목록 캐시 + 상세 캐시
./gradlew :apps:commerce-api:test --tests "*ProductQueryServiceTest"
./gradlew :apps:commerce-api:test --tests "*ProductQueryFacadeTest"

# 4. 캐시 무효화 + Read Model 동기화
./gradlew :apps:commerce-api:test --tests "*ProductCommandServiceTest"
./gradlew :apps:commerce-api:test --tests "*ProductCommandFacadeTest"
./gradlew :apps:commerce-api:test --tests "*BrandCommandFacadeTest"

# 5. E2E 테스트
./gradlew :apps:commerce-api:test --tests "*ProductControllerE2ETest"

# 6. 성능 측정 (API 레벨)
./gradlew :apps:commerce-api:benchmarkTest --tests "*ProductApiPerformanceTest"

# 7. 전체 빌드 (ArchUnit 포함)
./gradlew :apps:commerce-api:test
```
