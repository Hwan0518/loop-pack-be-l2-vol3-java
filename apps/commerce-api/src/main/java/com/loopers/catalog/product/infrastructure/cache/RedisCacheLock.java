package com.loopers.catalog.product.infrastructure.cache;


import com.loopers.config.redis.RedisConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;


/**
 * Redis SETNX 기반 분산 캐시 락
 * - 분산 환경(multi-JVM)에서 사용
 * - 현재는 대기 상태. 분산 환경 전환 시 @Primary 이동
 */
@Component
public class RedisCacheLock implements CacheLock {

	// redis
	private final RedisTemplate<String, String> redisTemplate;

	private static final Duration LOCK_TTL = Duration.ofSeconds(5);
	private static final long WAIT_MILLIS = 50;
	private static final int MAX_WAIT_RETRIES = 20;


	public RedisCacheLock(
		@Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate
	) {
		this.redisTemplate = redisTemplate;
	}


	/**
	 * Redis SETNX 기반 분산 락 실행
	 * 1. executeWithLock — SETNX로 락 획득 후 loader 실행, 실패 시 대기 후 재시도
	 */

	// 1. executeWithLock
	@Override
	public <T> T executeWithLock(String key, Supplier<T> loader) {

		// 락 키 생성
		String lockKey = key + ":lock";

		// SETNX로 락 획득 시도 (TTL 5초)
		Boolean acquired = redisTemplate.opsForValue()
			.setIfAbsent(lockKey, "1", LOCK_TTL);

		try {
			if (Boolean.TRUE.equals(acquired)) {
				// 락 획득 성공 → loader 실행
				return loader.get();
			} else {
				// 락 미획득 → 락 해제 대기 후 loader 재실행 (double-check 포함)
				waitForLockRelease(lockKey);
				return loader.get();
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return loader.get();
		} finally {
			// 락 획득 성공한 경우에만 해제
			if (Boolean.TRUE.equals(acquired)) {
				redisTemplate.delete(lockKey);
			}
		}
	}


	// 락 해제 대기 (락 보유 스레드의 캐시 저장 완료를 기다림)
	private void waitForLockRelease(String lockKey) throws InterruptedException {
		for (int i = 0; i < MAX_WAIT_RETRIES; i++) {
			Thread.sleep(WAIT_MILLIS);
			if (!Boolean.TRUE.equals(redisTemplate.hasKey(lockKey))) {
				return;
			}
		}
	}

}
