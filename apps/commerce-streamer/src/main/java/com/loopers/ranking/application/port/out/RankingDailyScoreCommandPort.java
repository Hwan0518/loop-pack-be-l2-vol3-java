package com.loopers.ranking.application.port.out;


import com.loopers.ranking.application.dto.RankingDailyKey;

import java.util.Map;


/**
 * 랭킹 일간 점수 스냅샷 쓰기 포트 (application → infrastructure 계약)
 * - consumer: organic_score 만 갱신 (carry_score 미변경)
 * - carry-over: carry_score 만 갱신 (organic_score 미변경)
 *
 * 1. organic_score 배치 upsert (consumer 전용)
 */
public interface RankingDailyScoreCommandPort {

	/**
	 * organic_score 배치 upsert
	 * - INSERT: organic_score = 전달값, carry_score = 0
	 * - UPDATE: organic_score 만 덮어씀, carry_score 유지
	 *
	 * @param organicScores (statDate, productId) → organicScore
	 * @param scorerType 현재 활성 scorer 타입 (e.g. "SATURATION")
	 */
	void upsertOrganicScores(Map<RankingDailyKey, Double> organicScores, String scorerType);

}
