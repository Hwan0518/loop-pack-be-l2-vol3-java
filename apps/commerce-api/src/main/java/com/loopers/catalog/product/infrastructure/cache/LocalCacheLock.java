package com.loopers.catalog.product.infrastructure.cache;


import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;


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


	/**
	 * key-level 락 실행
	 * 1. executeWithLock — 같은 key 요청은 직렬화, 다른 key는 병렬
	 */

	// 1. executeWithLock
	@Override
	public <T> T executeWithLock(String key, Supplier<T> loader) {

		// key별 락 객체 생성 (이미 존재하면 기존 객체 반환)
		Object lock = locks.computeIfAbsent(key, k -> new Object());

		// 같은 key에 대해 직렬화 실행
		synchronized (lock) {
			try {
				return loader.get();
			} finally {
				locks.remove(key);
			}
		}
	}

}
