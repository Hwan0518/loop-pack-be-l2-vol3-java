# 캐시 2계층 아키텍처 구현 설계

> **문서 상태**
> - 성격: 현재 구현에 가장 가까운 설계 문서
> - 전환기 TODO 항목 모두 완료 (`evictByPattern()` 제거, old key 정리)
> - 현재 구현 판단은 실제 코드 우선

## 1. 현재 상태 → 목표 상태

| 항목 | 현재 (AS-IS) | 목표 (TO-BE) |
|------|-------------|-------------|
| 목록 캐시 키 | `products:list:{brand\|all}:{sort}:{page}:{size}` | `products:ids:v1:{brand\|all}:{sort}:{page}:{size}` |
| 목록 캐시 값 | `ProductPageOutDto` (전체 DTO) | `IdListCacheEntry` (ids + totalElements) |
| 상세 캐시 키 | `product:{productId}` | `product:v1:{productId}` |
| 상세 캐시 값 | `ProductDetailOutDto` | `ProductCacheDto` (PLP+PDP 공용) |
| TTL (목록) | 5분 | 3분 |
| TTL (상세) | 10분 | 2분 |
| 쓰기 시 동작 | `evictByPattern("products:list:*")` + `evict("product:"+id)` | write-through (targeted refresh) |
| PLP 조회 흐름 | 캐시에서 전체 DTO 반환 | ID 리스트 → MGET → partial miss fill |
| 캐시 적용 조건 | 무제한 | `page < MAX_CACHEABLE_PAGE && size == DEFAULT_PAGE_SIZE` |
| 정렬 보조 키 | 없음 | `id` (tie-breaker) |

---

## 2. 신규/변경 파일 목록

### 2.1 신규 생성

| 파일 | 위치 | 역할 |
|------|------|------|
| `ProductCacheConstants` | `infrastructure/cache/` | 캐시 키 접두사, 버전 상수, DEFAULT_PAGE_SIZE |
| `ProductCacheDto` | `infrastructure/cache/` | PLP+PDP 공용 캐시 DTO (상세 캐시 값) |
| `IdListCacheEntry` | `infrastructure/cache/` | ID 리스트 캐시 값 record (ids, totalElements) |

### 2.2 변경 대상

| 파일 | 변경 내용 |
|------|----------|
| `ProductCacheManager` | write-through 메서드 추가, MGET 추가 |
| `ProductQueryService` | 2계층 조회 흐름 (ID 리스트 → MGET), cacheable guard, TTL 변경, `findActiveIdsByBrandId()` 추가 |
| `ProductCommandService` | evict → write-through 호출로 전환 |
| `ProductCommandFacade` | write-through 호출 위치 조정 (read model 동기화 이후) |
| `BrandCommandFacade` | 브랜드명 수정 시 상품 상세 캐시 write-through 추가 |
| `ProductQuerydslRepository` | tie-breaker 추가, `searchProductIds()` 추가, `findProductCacheDtosByIds()` bulk projection 추가 |
| `ProductReadModelJpaRepository` | `findActiveIdsByBrandId()` 추가 |
| `ProductReadModelRepository` | `findActiveIdsByBrandId()` 인터페이스 추가 |
| `ProductReadModelRepositoryImpl` | `findActiveIdsByBrandId()` 구현 추가 |

---

## 3. 상세 설계

### 3.1 ProductCacheConstants

```java
public final class ProductCacheConstants {
    public static final String CACHE_VERSION = "v1";
    public static final String DETAIL_KEY_PREFIX = "product:" + CACHE_VERSION + ":";
    public static final String ID_LIST_KEY_PREFIX = "products:ids:" + CACHE_VERSION + ":";
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_CACHEABLE_PAGE = 2;
    public static final Duration ID_LIST_TTL = Duration.ofMinutes(3);
    public static final Duration DETAIL_TTL = Duration.ofMinutes(2);
}
```

### 3.2 ProductCacheDto

PLP와 PDP에서 공용으로 사용하는 캐시 DTO. Read Model에서 직접 projection.

```java
public record ProductCacheDto(
    Long id, Long brandId, String brandName, String name,
    BigDecimal price, Long stock, String description, Long likeCount
) {
    // PLP 응답용 변환
    public ProductOutDto toProductOutDto() { ... }
    // PDP 응답용 변환
    public ProductDetailOutDto toProductDetailOutDto() { ... }
}
```

### 3.3 IdListCacheEntry

```java
public record IdListCacheEntry(List<Long> ids, long totalElements) {}
```

### 3.4 ProductCacheManager 신규 메서드

CacheManager는 Redis 전용 유틸로서의 책임만 유지. DB 조회는 Supplier로 위임.

```java
// 1. 상품 상세 캐시 write-through
public void refreshProductDetail(Long productId, Supplier<ProductCacheDto> loader) {
    ProductCacheDto dto = loader.get();
    put(DETAIL_KEY_PREFIX + productId, dto, DETAIL_TTL);
}

// 2. ID 리스트 캐시 write-through (단건, Supplier 기반)
public void refreshIdList(String cacheKey, Supplier<IdListCacheEntry> loader) {
    IdListCacheEntry entry = loader.get();
    put(cacheKey, entry, ID_LIST_TTL);
}

// 3. 상품 상세 캐시 삭제 (상품 삭제 시 예외적 사용)
public void deleteProductDetail(Long productId) {
    evict(DETAIL_KEY_PREFIX + productId);
}

// 4. MGET (여러 상품 상세 일괄 조회)
public List<ProductCacheDto> mgetProductDetails(List<Long> productIds) {
    List<String> keys = productIds.stream()
        .map(id -> DETAIL_KEY_PREFIX + id)
        .toList();
    // RedisTemplate multiGet → 역직렬화 → null 포함 리스트 반환
}
```

> **설계 변경 (Codex 피드백 반영)**: `refreshIdLists(productId, brandId, RefreshType)` 메서드 제거.
> CacheManager가 DB 조회와 트리거 매핑 로직을 갖는 것은 책임 불일치.
> ID list write-through 호출은 Service/Facade에서 직접 수행하되,
> 좋아요/재고 같은 고빈도 경로에서는 ID list write-through를 하지 않는다 (아래 3.6 참조).

### 3.5 ProductQueryService 2계층 조회 흐름

```java
// 사용자 상품 목록 검색 (2계층)
@Transactional(readOnly = true)
public ProductPageOutDto searchProducts(Long brandId, ProductSortType sortType, int page, int size) {

    // 캐시 적용 조건 확인
    if (!isCacheable(page, size)) {
        // 캐시 미적용 — DB 직접 조회
        return searchFromDb(brandId, sortType, page, size);
    }

    // 1. ID 리스트 캐시 조회 (cache-aside)
    String idListKey = buildIdListCacheKey(brandId, sortType, page, size);
    IdListCacheEntry idList = productCacheManager.getOrLoad(
        idListKey, IdListCacheEntry.class, ID_LIST_TTL,
        () -> loadIdListFromDb(brandId, sortType, page, size)
    );

    // 2. MGET 상세 캐시
    List<ProductCacheDto> cached = productCacheManager.mgetProductDetails(idList.ids());

    // 3. partial miss 처리
    List<Long> missedIds = extractMissedIds(idList.ids(), cached);
    if (!missedIds.isEmpty()) {
        List<ProductCacheDto> fromDb = loadAndCacheDetails(missedIds);
        cached = mergeInOrder(idList.ids(), cached, fromDb);
    }

    // 4. dangling ID 방어 (null skip)
    List<ProductOutDto> content = cached.stream()
        .filter(Objects::nonNull)
        .map(ProductCacheDto::toProductOutDto)
        .toList();

    return new ProductPageOutDto(content, page, size, idList.totalElements());
}

private boolean isCacheable(int page, int size) {
    return page < MAX_CACHEABLE_PAGE && size == DEFAULT_PAGE_SIZE;
}
```

#### loadAndCacheDetails — bulk projection (Read Model 기반)

```java
private List<ProductCacheDto> loadAndCacheDetails(List<Long> missedIds) {
    // ProductQuerydslRepository에서 Read Model bulk projection
    List<ProductCacheDto> dtos = productQueryPort.findProductCacheDtosByIds(missedIds);
    // 각 dto를 상세 캐시에 PUT
    for (ProductCacheDto dto : dtos) {
        productCacheManager.put(DETAIL_KEY_PREFIX + dto.id(), dto, DETAIL_TTL);
    }
    return dtos;
}
```

#### findActiveIdsByBrandId (브랜드명 write-through용)

```java
// ProductQueryService에 추가 — Facade가 호출
@Transactional(readOnly = true)
public List<Long> findActiveIdsByBrandId(Long brandId) {
    return productReadModelRepository.findActiveIdsByBrandId(brandId);
}
```

> **설계 변경 (Codex 피드백 반영)**: `findProductIdsByBrandId()` → `findActiveIdsByBrandId()`로 명칭 변경.
> 위치: `ProductCommandService`가 아닌 `ProductQueryService` (순수 read 메서드).
> Repository: `ProductReadModelRepository` 인터페이스에 추가 → `ProductReadModelRepositoryImpl`에서 JPA 위임.

### 3.6 ProductCommandService write-through 전환

#### 트리거별 캐시 갱신 범위

| 트리거 | 상세 캐시 | ID 리스트 캐시 | 근거 |
|--------|----------|--------------|------|
| 좋아요 ±1 | write-through | **갱신 안 함** (TTL 3분 자연 만료) | ±1로 순서 변동 극히 드묾. 고빈도 트리거에서 12 SQL/건은 비용 과다 |
| 재고 차감 | write-through | **갱신 안 함** (정렬 무관) | 재고는 정렬 기준이 아님 |
| 가격 수정 | write-through | write-through (PRICE_ASC × 6키) | 가격 변경은 저빈도, 순서 변동 직접적 |
| 상품 생성 | write-through | write-through (ALL × 18키) | 새 상품이 목록에 반영돼야 함 |
| 상품 삭제 | evict (삭제된 상품) | write-through (ALL × 18키) | 삭제 상품이 목록에서 제거돼야 함 |
| 브랜드명 수정 | write-through (해당 브랜드 전체) | 없음 (ID에 brandName 미포함) | 극히 저빈도, 상세만 영향 |

> **설계 변경 (Codex 피드백 반영)**: 좋아요/재고 경로에서 ID list write-through 제거.
> - 좋아요 1건당 기존 설계: 12 SQL (LIKES_DESC × 2 × 3pages × 2queries) → evictByPattern보다 비용 큼
> - 좋아요 ±1이 순서를 뒤집는 확률은 극히 낮고, TTL 3분이면 충분히 수렴
> - 재고는 정렬 기준 자체가 아니므로 ID list 영향 없음

```java
// 좋아요 증가 — 상세 캐시만 write-through (ID list 갱신 안 함)
@Transactional
public void increaseLikeCount(Long productId) {
    readModelRepository.increaseLikeCount(productId);

    // write-through: 상세 캐시만 (ID 리스트는 TTL 자연 만료)
    productCacheManager.refreshProductDetail(productId, () -> loadCacheDto(productId));
}

// 좋아요 감소 — 동일
@Transactional
public void decreaseLikeCount(Long productId) {
    readModelRepository.decreaseLikeCount(productId);

    // write-through: 상세 캐시만
    productCacheManager.refreshProductDetail(productId, () -> loadCacheDto(productId));
}

// 재고 차감 — 상세 캐시만 (재고는 정렬 무관)
@Transactional
public void decreaseStock(Long productId, Long quantity) {
    Product product = productQueryRepository.findActiveByIdForUpdate(productId)...;
    product.decreaseStock(quantity);
    productCommandRepository.save(product);
    readModelRepository.updateStock(productId, product.getStock().value());

    // write-through: 상세 캐시만
    productCacheManager.refreshProductDetail(productId, () -> loadCacheDto(productId));
}
```

#### 가격 변동 시 ID list write-through (저빈도, Facade에서 호출)

상품 수정(가격 변경 포함)은 `ProductCommandFacade`에서 수행하며,
read model 동기화 이후에 캐시 갱신을 호출한다.

```java
// ProductCommandFacade.updateProduct()
@Transactional
public AdminProductDetailOutDto updateProduct(Long id, AdminProductUpdateInDto inDto) {
    Product product = productQueryService.findActiveById(id);
    Product updatedProduct = productCommandService.updateProduct(product, inDto);

    Brand brand = brandQueryService.getBrandById(product.getBrandId());
    productCommandService.syncReadModel(updatedProduct, brand.getName().value());

    // write-through: 상세 캐시
    productCacheManager.refreshProductDetail(id, () -> loadCacheDto(id));
    // write-through: ID 리스트 (PRICE_ASC 정렬 영향)
    refreshIdListsForProduct(product.getBrandId(), ProductSortType.PRICE_ASC);

    return AdminProductDetailOutDto.from(updatedProduct, brand.getName().value());
}

// 상품 생성
@Transactional
public AdminProductDetailOutDto createProduct(AdminProductCreateInDto inDto) {
    Brand brand = brandQueryService.getBrandById(inDto.brandId());
    Product savedProduct = productCommandService.createProduct(inDto);
    productCommandService.syncReadModel(savedProduct, brand.getName().value());

    // write-through: 상세 캐시 + 모든 정렬 ID 리스트
    productCacheManager.refreshProductDetail(savedProduct.getId(), () -> loadCacheDto(savedProduct.getId()));
    refreshIdListsForAllSorts(savedProduct.getBrandId());

    return AdminProductDetailOutDto.from(savedProduct, brand.getName().value());
}

// 상품 삭제
@Transactional
public void deleteProduct(Long id) {
    Product product = productQueryService.findActiveById(id);
    productCommandService.deleteProduct(product);

    // 상세 캐시: evict (삭제된 상품)
    productCacheManager.deleteProductDetail(id);
    // ID 리스트: write-through (모든 정렬)
    refreshIdListsForAllSorts(product.getBrandId());
}

// --- private helpers ---

// 특정 정렬의 ID 리스트 write-through
private void refreshIdListsForProduct(Long brandId, ProductSortType sortType) {
    for (int page = 0; page < MAX_CACHEABLE_PAGE; page++) {
        // brandId 조건 + all 조건
        String brandKey = buildIdListCacheKey(brandId, sortType, page, DEFAULT_PAGE_SIZE);
        String allKey = buildIdListCacheKey(null, sortType, page, DEFAULT_PAGE_SIZE);
        productCacheManager.refreshIdList(brandKey, () -> loadIdListFromDb(brandId, sortType, page, DEFAULT_PAGE_SIZE));
        productCacheManager.refreshIdList(allKey, () -> loadIdListFromDb(null, sortType, page, DEFAULT_PAGE_SIZE));
    }
}

// 모든 정렬의 ID 리스트 write-through
private void refreshIdListsForAllSorts(Long brandId) {
    for (ProductSortType sort : ProductSortType.values()) {
        refreshIdListsForProduct(brandId, sort);
    }
}
```

> **설계 변경 (Codex 피드백 반영)**:
> 1. ID list write-through 호출을 Facade로 이동 (read model 동기화 이후에 캐시 갱신 보장)
> 2. `loadCacheDto()` 도 Facade의 private helper로 — Read Model에서 projection 1회 조회
> 3. `List.of(null, brandId)` NPE 제거 — 명시적으로 brandKey/allKey 분리 호출

#### loadCacheDto — Read Model projection

```java
// ProductCommandFacade (또는 ProductQueryService)의 private helper
private ProductCacheDto loadCacheDto(Long productId) {
    // Read Model에서 직접 ProductCacheDto projection
    return productQueryPort.findProductCacheDtoById(productId);
}
```

> **설계 변경 (Codex 피드백 반영)**: `loadCacheDto()`의 데이터 소스를 Product + BrandService 조합이 아닌
> `product_read_model` 테이블에서 직접 projection. brandName, description이 이미 비정규화되어 있으므로
> 1회 조회로 완성 가능. 이를 위해 `ProductQueryPort`에 `findProductCacheDtoById()` 추가.

### 3.7 BrandCommandFacade 브랜드명 write-through

```java
@Transactional
public AdminBrandDetailOutDto updateBrand(Long id, AdminBrandUpdateInDto inDto) {
    Brand brand = brandQueryService.getBrandById(id);
    Brand updatedBrand = brandCommandService.updateBrand(brand, inDto);

    // Read Model 브랜드명 동기화
    productCommandService.syncBrandNameInReadModel(id, updatedBrand.getName().value());

    // 상품 상세 캐시 write-through (해당 브랜드의 전체 상품)
    List<Long> productIds = productQueryService.findActiveIdsByBrandId(id);
    for (Long productId : productIds) {
        productCacheManager.refreshProductDetail(productId, () -> loadCacheDto(productId));
    }

    return AdminBrandDetailOutDto.from(updatedBrand);
}
```

> **설계 변경 (Codex 피드백 반영)**: `productCommandService.findProductIdsByBrandId()` →
> `productQueryService.findActiveIdsByBrandId()` (순수 read 메서드는 QueryService에 배치)

### 3.8 QueryDSL tie-breaker

```java
private OrderSpecifier<?>[] getOrderSpecifiers(ProductSortType sortType) {
    OrderSpecifier<?> primary = switch (sortType) {
        case LATEST -> readModel.createdAt.desc();
        case PRICE_ASC -> readModel.price.asc();
        case LIKES_DESC -> readModel.likeCount.desc();
    };
    // tie-breaker: 동률 시 id 내림차순 (최신 상품 우선)
    OrderSpecifier<?> secondary = readModel.id.desc();
    return new OrderSpecifier<?>[]{ primary, secondary };
}
```

### 3.9 ProductReadModelJpaRepository 추가 메서드

```java
// 브랜드 ID로 활성 상품 ID 목록 조회 (브랜드명 write-through용)
@Query("SELECT e.id FROM ProductReadModelEntity e WHERE e.brandId = :brandId AND e.deletedAt IS NULL")
List<Long> findActiveIdsByBrandId(@Param("brandId") Long brandId);
```

### 3.10 ProductQueryPort / QuerydslRepository 추가 메서드

```java
// ProductQueryPort 인터페이스에 추가
ProductCacheDto findProductCacheDtoById(Long productId);
List<ProductCacheDto> findProductCacheDtosByIds(List<Long> productIds);

// ProductQuerydslRepository — Read Model에서 ProductCacheDto projection
public ProductCacheDto findProductCacheDtoById(Long productId) {
    return queryFactory.select(Projections.constructor(ProductCacheDto.class,
            readModel.id, readModel.brandId, readModel.brandName, readModel.name,
            readModel.price, readModel.stock, readModel.description, readModel.likeCount))
        .from(readModel)
        .where(readModel.id.eq(productId).and(readModel.deletedAt.isNull()))
        .fetchOne();
}

public List<ProductCacheDto> findProductCacheDtosByIds(List<Long> productIds) {
    return queryFactory.select(Projections.constructor(ProductCacheDto.class,
            readModel.id, readModel.brandId, readModel.brandName, readModel.name,
            readModel.price, readModel.stock, readModel.description, readModel.likeCount))
        .from(readModel)
        .where(readModel.id.in(productIds).and(readModel.deletedAt.isNull()))
        .fetch();
}
```

---

## 4. ID 리스트 조회 쿼리

write-through 시 ID 리스트를 재생성하려면 "해당 정렬 + 필터 조건으로 page N의 ID 목록"을 조회해야 한다.

```java
// ProductQuerydslRepository에 추가
public IdListCacheEntry searchProductIds(ProductSearchCriteria criteria, PageCriteria pageCriteria) {

    QProductReadModelEntity readModel = QProductReadModelEntity.productReadModelEntity;

    // 활성 상품 필터
    BooleanExpression where = readModel.deletedAt.isNull();
    if (criteria.brandId() != null) {
        where = where.and(readModel.brandId.eq(criteria.brandId()));
    }

    // 총 개수
    long total = queryFactory.select(readModel.id)
        .from(readModel)
        .where(where)
        .fetchCount();

    // ID 목록 (정렬 + 페이지네이션)
    List<Long> ids = queryFactory.select(readModel.id)
        .from(readModel)
        .where(where)
        .orderBy(getOrderSpecifiers(criteria.sortType()))
        .offset((long) pageCriteria.page() * pageCriteria.size())
        .limit(pageCriteria.size())
        .fetch();

    return new IdListCacheEntry(ids, total);
}
```

---

## 5. 구현 Phase

### Phase 1: Foundation (인프라 변경)
1. `ProductCacheConstants`, `ProductCacheDto`, `IdListCacheEntry` 생성
2. `ProductCacheManager`에 신규 메서드 추가 (refreshProductDetail, refreshIdList, mgetProductDetails, deleteProductDetail)
3. `ProductReadModelRepository` 인터페이스 + 구현체에 `findActiveIdsByBrandId()` 추가
4. `ProductReadModelJpaRepository`에 `findActiveIdsByBrandId()` 추가
5. `ProductQueryPort`에 `findProductCacheDtoById()`, `findProductCacheDtosByIds()` 추가
6. `ProductQuerydslRepository`에 tie-breaker 추가 + `searchProductIds()` + `findProductCacheDto*` 추가
7. 기존 기능에 영향 없음 — 새 코드만 추가

### Phase 2: Write path (write-through 전환)
1. `ProductCommandService`의 좋아요/재고 메서드: evict → 상세 캐시 write-through로 교체 (ID list 갱신 안 함)
2. `ProductCommandFacade`의 생성/수정/삭제: read model 동기화 이후 write-through 호출
3. `BrandCommandFacade.updateBrand()`에 상품 상세 캐시 write-through 추가
4. `ProductQueryService`에 `findActiveIdsByBrandId()` 추가
5. 기존 `evictProductDetailCache()` 메서드 → `deleteProductDetail()`로 교체
6. ~~**전환기 병행**: write-through와 함께 기존 키 evict 병행~~ → 전환 완료, old key 병행 불필요

### Phase 3: Read path (2계층 조회)
1. `ProductQueryService.searchProducts()` 전면 교체 (ID 리스트 → MGET 흐름)
2. cacheable guard 적용 (`page < MAX_CACHEABLE_PAGE && size == DEFAULT_PAGE_SIZE`)
3. `ProductQueryFacade` 상세 조회 시 `ProductCacheDto` 사용
4. TTL 변경 (list 3분, detail 2분)
5. 기존 `products:list:*` 캐시 키 → `products:ids:v1:*` 전환
6. 기존 `product:{id}` 캐시 키 → `product:v1:{id}` 전환
7. ~~Phase 2의 old key evict 병행 코드 제거~~ → 병행 코드 없이 직접 전환 완료
8. `evictByPattern()` 메서드 ProductCacheManager에서 제거 완료

---

## 6. 테스트 전략

### Phase 1 테스트
- `ProductCacheDto` 변환 테스트 (toProductOutDto, toProductDetailOutDto)
- `ProductQuerydslRepository.searchProductIds()` 통합 테스트
- `ProductQuerydslRepository.findProductCacheDtoById/sByIds()` 통합 테스트
- tie-breaker 정렬 검증 (동률 시 id 내림차순)

### Phase 2 테스트
- `ProductCommandService` 좋아요/재고 메서드의 상세 캐시 write-through 호출 검증 (mock 기반)
- `ProductCommandFacade` 생성/수정/삭제의 write-through 호출 순서 검증
- `BrandCommandFacade.updateBrand()` 시 상품 상세 캐시 갱신 검증
- 통합 테스트: 좋아요 → 상세 캐시 값 변경 확인

### Phase 3 테스트
- `ProductQueryService.searchProducts()` 2계층 흐름 검증
  - ID 리스트 hit + 상세 all hit
  - ID 리스트 hit + 상세 partial miss
  - ID 리스트 miss
  - dangling ID 방어
- cacheable guard: page 3 이상 또는 size != 20일 때 캐시 미사용
- E2E: 상품 생성 → 목록 조회 → write-through 반영 확인

---

## 7. 주의사항

### 전환 완료 상태
- old key 병행 로직 없이 직접 전환 완료
- `evictByPattern()` 메서드 ProductCacheManager에서 제거 완료
- old key(`products:list:*`, `product:{id}`) 관련 artifact 정리 완료

### 고빈도 트리거 최적화
- 좋아요/재고 경로는 **상세 캐시만 write-through**, ID 리스트는 TTL 3분 자연 만료에 위임
- 좋아요 ±1로 목록 순서가 뒤집히는 확률은 극히 낮으므로 eventual consistency 허용

### PER 정책
- PER(Probabilistic Early Refresh)는 ID 리스트 캐시에서 제거, 상세 캐시에서만 유지
- write-through가 주력이므로 PER의 역할이 줄어듦. Redis 장애 시 PER도 동일하게 실패하므로 추가 방어 효과 없음

### 캐시 쓰기 시점
- 캐시 write-through는 TX 내에서 실행됨 (기존 evict와 동일한 수준)
- best-effort — TX rollback 시 phantom cache가 남을 수 있으나 TTL 2분 내 자연 소멸
- afterCommit 패턴은 코드 복잡도 대비 실익이 낮으므로 현재 scope에서 제외

### Redis replica lag
- 읽기 replica-preferred, 쓰기 master 구조이므로 write-through 직후 읽기에서 stale 가능
- write-through로 miss 자체가 줄어들어 실질적 영향은 미미
