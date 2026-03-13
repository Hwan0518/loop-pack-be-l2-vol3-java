package com.loopers.catalog.product.infrastructure.cache.lock;


import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;


/**
 * JVM 로컬 key-level 캐시 락
 * - ConcurrentHashMap + ref-counted ReentrantLock으로 같은 key 요청만 직렬화
 * - 다른 key 요청은 병렬 처리 (key 단위 세밀한 락)
 * - 단일 서버 환경에서 사용. 분산 환경 전환 시 RedisCacheLock으로 @Primary 이동
 */
@Primary
@Component
public class LocalCacheLock implements CacheLock {

	private final ConcurrentHashMap<String, LockHolder> locks = new ConcurrentHashMap<>();


	/**
	 * key-level 락 실행
	 * 1. executeWithLock — 같은 key 요청은 직렬화, 다른 key는 병렬
	 */

	// 1. executeWithLock
	@Override
	public <T> T executeWithLock(String key, Supplier<T> loader) {
		LockHolder holder = locks.compute(key, (ignored, existing) -> {
			if (existing == null) {
				return new LockHolder();
			}
			existing.retain();
			return existing;
		});

		holder.lock();
		try {
			return loader.get();
		} finally {
			holder.unlock();
			locks.compute(key, (ignored, existing) -> {
				if (existing != holder) {
					return existing;
				}
				return holder.release() ? null : holder;
			});
		}
	}

	private static final class LockHolder {

		private final ReentrantLock lock = new ReentrantLock();
		private final AtomicInteger refCount = new AtomicInteger(1);

		private void retain() {
			refCount.incrementAndGet();
		}

		private void lock() {
			lock.lock();
		}

		private void unlock() {
			lock.unlock();
		}

		private boolean release() {
			return refCount.decrementAndGet() == 0;
		}
	}

}
