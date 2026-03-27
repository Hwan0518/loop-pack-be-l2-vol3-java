package com.loopers.catalog.product.application.port.out.cache;


import com.fasterxml.jackson.core.type.TypeReference;
import com.loopers.catalog.product.application.port.out.cache.dto.IdListCacheEntry;
import com.loopers.catalog.product.application.port.out.cache.dto.ProductCacheDto;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;


/**
 * 상품 캐시 포트
 * - application 계층에서 인프라(Redis) 캐시에 접근하기 위한 계약
 * - 구현체: infrastructure/cache/ProductCacheManager
 *
 * 1. get(key, Class) — 단순 타입 캐시 조회
 * 2. get(key, TypeReference) — 제네릭 타입 캐시 조회
 * 3. put(key, value, ttl) — 캐시 저장 (TTL jitter 포함)
 * 4. evict(key) — 단일 키 삭제
 * 5. getOrLoad(key, type, ttl, loader) — Cache-Aside + 스탬피드 보호
 * 6. getOrLoadWithPer(key, type, ttl, loader) — getOrLoad + PER (TTL 임박 시 확률적 갱신)
 * 7. refreshProductDetail(productId, loader) — 상품 상세 캐시 write-through
 * 8. refreshIdList(cacheKey, loader) — ID 리스트 캐시 write-through (단건)
 * 9. deleteProductDetail(productId) — 상품 상세 캐시 삭제
 * 10. mgetProductDetails(productIds) — 여러 상품 상세 일괄 조회 (MGET)
 */
public interface ProductCachePort {

	// 1. 단순 타입 캐시 조회
	<T> Optional<T> get(String key, Class<T> type);

	// 2. 제네릭 타입 캐시 조회
	<T> Optional<T> get(String key, TypeReference<T> typeRef);

	// 3. 캐시 저장 (TTL jitter 포함)
	void put(String key, Object value, Duration ttl);

	// 4. 단일 키 삭제
	void evict(String key);

	// 5. Cache-Aside + 스탬피드 보호 (CacheLock + double-check)
	<T> T getOrLoad(String key, Class<T> type, Duration ttl, Supplier<T> loader);

	// 6. Cache-Aside + PER (Probabilistic Early Refresh) + 스탬피드 보호
	<T> T getOrLoadWithPer(String key, Class<T> type, Duration ttl, Supplier<T> loader);

	// 7. 상품 상세 캐시 write-through (Supplier 기반)
	void refreshProductDetail(Long productId, Supplier<ProductCacheDto> loader);

	// 8. ID 리스트 캐시 write-through (단건, Supplier 기반)
	void refreshIdList(String cacheKey, Supplier<IdListCacheEntry> loader);

	// 9. 상품 상세 캐시 삭제 (상품 삭제 시 예외적 사용)
	void deleteProductDetail(Long productId);

	// 10. 여러 상품 상세 일괄 조회 (MGET)
	List<ProductCacheDto> mgetProductDetails(List<Long> productIds);

}
