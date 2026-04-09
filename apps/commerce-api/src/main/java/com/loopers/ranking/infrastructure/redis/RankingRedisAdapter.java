package com.loopers.ranking.infrastructure.redis;


import com.loopers.config.redis.RedisConfig;
import com.loopers.ranking.application.port.out.ProductScoreEntry;
import com.loopers.ranking.application.port.out.RankingRedisPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;


@Component
public class RankingRedisAdapter implements RankingRedisPort {

	private static final String ZSET_PREFIX = "ranking:all:";

	private final RedisTemplate<String, String> readTemplate;
	private final RedisTemplate<String, String> writeTemplate;


	public RankingRedisAdapter(
		RedisTemplate<String, String> readTemplate,
		@Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> writeTemplate
	) {
		this.readTemplate = readTemplate;
		this.writeTemplate = writeTemplate;
	}


	/**
	 * 랭킹 Redis 어댑터 (commerce-api)
	 * - 조회: default template (REPLICA_PREFERRED)
	 * - 쓰기 (carryOver, setTtl): master template
	 *
	 * 1. 상위 N개 조회 (ZREVRANGE with scores)
	 * 2. ZSET 전체 크기
	 * 3. 특정 상품의 역순 순위 (0-based)
	 * 4. carry-over (ZUNIONSTORE + WEIGHTS)
	 * 5. TTL 설정
	 */

	// 1. 상위 N개 조회 (ZREVRANGE with scores)
	@Override
	public List<ProductScoreEntry> getTopProducts(String dateStr, long offset, long count) {
		String key = ZSET_PREFIX + dateStr;
		Set<TypedTuple<String>> tuples = readTemplate.opsForZSet()
			.reverseRangeWithScores(key, offset, offset + count - 1);

		if (tuples == null || tuples.isEmpty()) {
			return List.of();
		}

		List<ProductScoreEntry> entries = new ArrayList<>();
		for (TypedTuple<String> tuple : tuples) {
			Long productId = Long.valueOf(tuple.getValue());
			double score = tuple.getScore() != null ? tuple.getScore() : 0.0;
			entries.add(new ProductScoreEntry(productId, score));
		}
		return entries;
	}


	// 2. ZSET 전체 크기
	@Override
	public long getZSetSize(String dateStr) {
		Long size = readTemplate.opsForZSet().zCard(ZSET_PREFIX + dateStr);
		return size != null ? size : 0;
	}


	// 3. 특정 상품의 역순 순위 (0-based, 없으면 null)
	@Override
	public Long getReversedRank(String dateStr, Long productId) {
		return readTemplate.opsForZSet().reverseRank(ZSET_PREFIX + dateStr, productId.toString());
	}


	// 4. carry-over: ZUNIONSTORE dest 1 src WEIGHTS weight (Lua script)
	@Override
	public void carryOver(String fromDateStr, String toDateStr, double weight) {
		String srcKey = ZSET_PREFIX + fromDateStr;
		String destKey = ZSET_PREFIX + toDateStr;

		String luaScript =
			"redis.call('ZUNIONSTORE', KEYS[1], 1, KEYS[2], 'WEIGHTS', ARGV[1]) " +
			"return 1";
		writeTemplate.execute(
			new org.springframework.data.redis.core.script.DefaultRedisScript<>(luaScript, Long.class),
			List.of(destKey, srcKey),
			String.valueOf(weight)
		);
	}


	// 5. TTL 설정
	@Override
	public void setTtl(String dateStr, Duration ttl) {
		writeTemplate.expire(ZSET_PREFIX + dateStr, ttl);
	}

}
