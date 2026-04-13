package com.loopers.ranking.application.port.out;


import java.time.Duration;


/**
 * 랭킹 Redis 포트 (commerce-streamer)
 * - 일간 카운터 HASH 갱신 + ZSET 점수 갱신
 */
public interface RankingRedisPort {

	/**
	 * 1. 일간 카운터 HINCRBY + 이전/이후 카운트 반환
	 * - ranking:counter:view:{dateStr}, ranking:counter:like:{dateStr}, ranking:counter:order:{dateStr}
	 */
	CounterResult incrementCounterAndGetCounts(String dateStr, Long productId,
		long viewDelta, long likeDelta, long orderDelta);


	/**
	 * 2. ZSET 점수 증분 (ZINCRBY ranking:all:{dateStr})
	 */
	void incrementScore(String dateStr, Long productId, double scoreDelta);


	/**
	 * 3. TTL 보장 (ZSET + 3 counter keys)
	 */
	void ensureTtl(String dateStr, Duration ttl);

}
