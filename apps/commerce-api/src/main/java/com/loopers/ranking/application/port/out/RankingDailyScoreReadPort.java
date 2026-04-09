package com.loopers.ranking.application.port.out;


import java.time.LocalDate;
import java.util.List;


/**
 * 랭킹 일간 점수 스냅샷 읽기 포트 (commerce-api — carry-over / rebuild 용)
 * - ranking_daily_score 테이블에서 특정 날짜 + scorer_type 의 전체 row 조회
 *
 * 1. 특정 날짜 전체 점수 스냅샷 조회
 */
public interface RankingDailyScoreReadPort {

	/**
	 * 특정 날짜 + scorerType 의 전체 점수 스냅샷 조회
	 * @return (productId, organic_score, carry_score) 목록
	 */
	List<ScoreSnapshot> findByDateAndScorerType(LocalDate statDate, String scorerType);


	/**
	 * 점수 스냅샷 projection
	 */
	record ScoreSnapshot(
		Long productId,
		double organicScore,
		double carryScore
	) {
		public double totalScore() {
			return organicScore + carryScore;
		}
	}

}
