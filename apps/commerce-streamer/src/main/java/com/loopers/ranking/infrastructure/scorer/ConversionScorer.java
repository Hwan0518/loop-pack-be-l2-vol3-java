package com.loopers.ranking.infrastructure.scorer;


import com.loopers.ranking.application.port.out.RankingScorer;


/**
 * 전환율 기반 점수 계산기
 * - "본 사람 중 몇 명이 샀나" 관점 — 숨은 보석 상품 발굴에 유용
 * - 절대량이 아닌 조회 대비 전환율(order/view, like/view)을 중시
 * - 최소 조회수 신뢰도(confidence)로 극소 뷰 상품의 과대평가 방어
 *
 * 공식:
 *   orderRate = order / view (조회 대비 주문 전환율)
 *   likeRate  = like / view  (조회 대비 좋아요 전환율)
 *   confidence = sat(view, 10) (조회수 10 이상이면 신뢰도 0.5+)
 *   score = confidence * (0.65 * sat(orderRate*100, 30) + 0.35 * sat(likeRate*100, 50))
 *
 * 주의: Bean 등록하지 않음 (기본은 SaturationScorer)
 * - 사용 시 @Primary/@Qualifier 또는 @Profile로 교체
 */
public class ConversionScorer implements RankingScorer {

	// 전환율 가중치
	private static final double WEIGHT_ORDER_RATE = 0.65;
	private static final double WEIGHT_LIKE_RATE = 0.35;

	// 전환율 saturation k (orderRate*100 기준 — 30%면 포화의 절반)
	private static final double K_ORDER_RATE = 30.0;
	// likeRate*100 기준 — 50%면 포화의 절반
	private static final double K_LIKE_RATE = 50.0;

	// 최소 조회수 신뢰도 k (view 10이면 confidence 0.5)
	private static final double K_CONFIDENCE = 10.0;


	@Override
	public double calculateScore(long view, long like, long order) {
		if (view <= 0) return 0.0;

		// 전환율 계산 (0~1 범위, *100 해서 % 스케일)
		double orderRate = (double) order / view * 100.0;
		double likeRate = (double) like / view * 100.0;

		// 최소 조회수 신뢰도 — 극소 뷰 상품 과대평가 방어
		double confidence = sat(view, K_CONFIDENCE);

		// 전환율 × 신뢰도
		return confidence * (
			WEIGHT_ORDER_RATE * sat(orderRate, K_ORDER_RATE) +
			WEIGHT_LIKE_RATE * sat(likeRate, K_LIKE_RATE)
		);
	}


	// saturation 함수: sat(x, k) = x / (x + k)
	private static double sat(double x, double k) {
		if (x <= 0) return 0.0;
		return x / (x + k);
	}

	// long 오버로드
	private static double sat(long x, double k) {
		return sat((double) x, k);
	}

}
