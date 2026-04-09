package com.loopers.ranking.application.port.out;


import java.time.LocalDate;
import java.util.Map;


/**
 * 랭킹 일간 점수 스냅샷 carry_score 쓰기 포트 (commerce-api — carry-over 스케줄러 전용)
 * - 내일 날짜에 carry_score upsert (organic_score = 0)
 *
 * 1. carry_score 배치 upsert
 */
public interface RankingDailyScoreCarryWritePort {

	/**
	 * carry_score 배치 upsert
	 * - INSERT: organic_score=0, carry_score=전달값
	 * - UPDATE: carry_score 만 덮어쓰기 (organic_score 보존)
	 *
	 * @param statDate 내일 날짜
	 * @param scorerType scorer 타입
	 * @param carryScores productId → carry_score
	 */
	void upsertCarryScores(LocalDate statDate, String scorerType, Map<Long, Double> carryScores);

}
