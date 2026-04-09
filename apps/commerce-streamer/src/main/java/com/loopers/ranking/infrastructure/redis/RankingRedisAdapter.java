package com.loopers.ranking.infrastructure.redis;


import com.loopers.config.redis.RedisConfig;
import com.loopers.ranking.application.port.out.CounterResult;
import com.loopers.ranking.application.port.out.RankingRedisPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;


@Component
public class RankingRedisAdapter implements RankingRedisPort {

	private static final String ZSET_PREFIX = "ranking:all:";
	private static final String COUNTER_VIEW_PREFIX = "ranking:counter:view:";
	private static final String COUNTER_LIKE_PREFIX = "ranking:counter:like:";
	private static final String COUNTER_ORDER_PREFIX = "ranking:counter:order:";

	private final RedisTemplate<String, String> redisTemplate;


	public RankingRedisAdapter(
		@Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> redisTemplate
	) {
		this.redisTemplate = redisTemplate;
	}


	/**
	 * 랭킹 Redis 어댑터 (commerce-streamer)
	 * - master-only RedisTemplate 사용 (쓰기 전용)
	 * - HASH: ranking:counter:{type}:{yyyyMMdd} — 일간 카운터
	 * - ZSET: ranking:all:{yyyyMMdd} — 종합 랭킹
	 *
	 * 1. 일간 카운터 HINCRBY + 이전/이후 카운트 반환
	 * 2. ZSET 점수 증분
	 * 3. TTL 보장 (ZSET + 3 counter keys)
	 */

	// 1. 일간 카운터 HINCRBY + 이전/이후 카운트 반환
	@Override
	public CounterResult incrementCounterAndGetCounts(String dateStr, Long productId,
		long viewDelta, long likeDelta, long orderDelta) {

		String field = productId.toString();

		// HINCRBY — 반환값이 갱신 후 값
		long newView = hincrby(COUNTER_VIEW_PREFIX + dateStr, field, viewDelta);
		long newLike = hincrby(COUNTER_LIKE_PREFIX + dateStr, field, likeDelta);
		long newOrder = hincrby(COUNTER_ORDER_PREFIX + dateStr, field, orderDelta);

		// old = new - delta
		return new CounterResult(
			newView - viewDelta, newView,
			newLike - likeDelta, newLike,
			newOrder - orderDelta, newOrder
		);
	}


	// 2. ZSET 점수 증분
	@Override
	public void incrementScore(String dateStr, Long productId, double scoreDelta) {
		redisTemplate.opsForZSet().incrementScore(
			ZSET_PREFIX + dateStr, productId.toString(), scoreDelta);
	}


	// 3. TTL 보장 (ZSET + 3 counter keys)
	@Override
	public void ensureTtl(String dateStr, Duration ttl) {
		redisTemplate.expire(ZSET_PREFIX + dateStr, ttl);
		redisTemplate.expire(COUNTER_VIEW_PREFIX + dateStr, ttl);
		redisTemplate.expire(COUNTER_LIKE_PREFIX + dateStr, ttl);
		redisTemplate.expire(COUNTER_ORDER_PREFIX + dateStr, ttl);
	}


	/**
	 * private method
	 * - HINCRBY 실행 (delta=0이면 HGET 조회만 수행)
	 */

	// HINCRBY — delta가 0이면 현재 값만 조회 (불필요한 쓰기 방지)
	private long hincrby(String key, String field, long delta) {
		if (delta == 0) {
			String val = (String) redisTemplate.opsForHash().get(key, field);
			return val != null ? Long.parseLong(val) : 0L;
		}
		Long result = redisTemplate.opsForHash().increment(key, field, delta);
		return result != null ? result : 0L;
	}

}
