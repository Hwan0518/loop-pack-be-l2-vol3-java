package com.loopers.ranking.infrastructure.scorer;


import com.loopers.ranking.application.port.out.RankingScorer;


/**
 * 선형 가중합 점수 계산기 (대안 구현체)
 * - 정규화 없이 단순 가중합: weight * count
 * - 특징: 이벤트 수에 비례하여 선형 증가 (포화 없음)
 * - 용도: saturation이 필요 없는 단순 랭킹 테스트용
 *
 * 주의: Bean 등록하지 않음 (기본은 SaturationScorer)
 * - 사용 시 @Primary/@Qualifier 또는 @Profile로 교체
 */
public class LinearScorer implements RankingScorer {

	private static final double WEIGHT_VIEW = 0.1;
	private static final double WEIGHT_LIKE = 0.2;
	private static final double WEIGHT_ORDER = 0.6;

	// 정규화 기준값 (score가 1.0이 되는 기준)
	private static final double NORM_VIEW = 1000.0;
	private static final double NORM_LIKE = 100.0;
	private static final double NORM_ORDER = 30.0;


	@Override
	public double calculateScore(long view, long like, long order) {
		double viewScore = WEIGHT_VIEW * Math.min(1.0, view / NORM_VIEW);
		double likeScore = WEIGHT_LIKE * Math.min(1.0, like / NORM_LIKE);
		double orderScore = WEIGHT_ORDER * Math.min(1.0, order / NORM_ORDER);
		return viewScore + likeScore + orderScore;
	}

}
