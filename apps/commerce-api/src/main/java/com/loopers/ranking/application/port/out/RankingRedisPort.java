package com.loopers.ranking.application.port.out;


import java.time.Duration;
import java.util.List;


/**
 * 랭킹 Redis 포트 (commerce-api)
 * - ZSET 조회 + carry-over 쓰기
 */
public interface RankingRedisPort {

	/**
	 * 1. 상위 N개 조회 (ZREVRANGE with scores)
	 * @param offset 0-based 시작 위치
	 * @param count 조회 개수
	 */
	List<ProductScoreEntry> getTopProducts(String dateStr, long offset, long count);


	/**
	 * 2. ZSET 전체 크기 (ZCARD)
	 */
	long getZSetSize(String dateStr);


	/**
	 * 3. 특정 상품의 역순 순위 (ZREVRANK, 0-based, 없으면 null)
	 */
	Long getReversedRank(String dateStr, Long productId);


	/**
	 * 4. carry-over: ZUNIONSTORE dest 1 src WEIGHTS weight
	 */
	void carryOver(String fromDateStr, String toDateStr, double weight);


	/**
	 * 5. TTL 설정
	 */
	void setTtl(String dateStr, Duration ttl);

}
