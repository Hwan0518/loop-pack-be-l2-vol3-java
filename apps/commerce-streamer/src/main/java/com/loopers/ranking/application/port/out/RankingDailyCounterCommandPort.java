package com.loopers.ranking.application.port.out;


import com.loopers.ranking.application.dto.RankingDailyKey;

import java.util.Map;
import java.util.Set;


/**
 * 랭킹 일간 카운터 쓰기 포트 (application → infrastructure 계약)
 * - (statDate, productId) 별 delta 를 누적 upsert 한다
 * - 음수 delta 도 그대로 반영 (clamp 없음 — scorer 입력 시점에만 clamp)
 * - 배치 upsert: 한 번의 호출에서 단일 트랜잭션 내 처리
 *
 * 1. 배치 upsert (delta 누적)
 * 2. 현재 누적 카운터 조회 (upsert 직후 DB 기준 절대값)
 */
public interface RankingDailyCounterCommandPort {

	/**
	 * (statDate, productId) → [viewDelta, likeDelta, orderDelta] 배치 upsert
	 * - INSERT ... ON DUPLICATE KEY UPDATE 패턴으로 누적
	 */
	void upsertDeltas(Map<RankingDailyKey, long[]> deltas);


	/**
	 * 현재 누적 카운터 조회 — DB 기준 절대값 반환
	 * @param keys 조회 대상 (statDate, productId)
	 * @return (statDate, productId) → [viewCount, likeCount, orderQty]
	 */
	Map<RankingDailyKey, long[]> getCounters(Set<RankingDailyKey> keys);

}
