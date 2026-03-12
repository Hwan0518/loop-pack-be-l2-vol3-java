package com.loopers.catalog.product.infrastructure.cache;


import java.util.function.Supplier;


/**
 * 캐시 스탬피드 방지용 key-level 락
 * - 같은 key에 대한 동시 DB 조회를 1회로 제한
 * - 구현체: LocalCacheLock (@Primary), RedisCacheLock (분산 환경 전환용)
 */
public interface CacheLock {

	<T> T executeWithLock(String key, Supplier<T> loader);

}
