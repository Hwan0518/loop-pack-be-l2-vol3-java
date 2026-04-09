package com.loopers.ranking.infrastructure.scorer;


import com.loopers.ranking.application.port.out.RankingScorer;
import org.springframework.stereotype.Component;


/**
 * Saturation 기반 점수 계산기 (기본 구현체)
 * - sat(x, k) = x / (x + k) — 0~1 범위로 수렴
 * - dailyScore = 0.15*sat(view,100) + 0.35*sat(like,10) + 0.50*sat(order,3)
 *
 * k값 설계 근거:
 * - view k=100: 행위 비용 0원, 비회원도 가능. 100회면 "꽤 관심받는" 기준
 * - like k=10: 로그인 필요, 의식적 행동. 10개면 강한 참여 신호
 * - order k=3: 실제 비용 발생, 가장 depth 깊은 행위. 3건이면 매우 강한 구매 신호
 */
@Component
public class SaturationScorer implements RankingScorer {

	private static final double WEIGHT_VIEW = 0.15;
	private static final double WEIGHT_LIKE = 0.35;
	private static final double WEIGHT_ORDER = 0.50;

	private static final double K_VIEW = 100.0;
	private static final double K_LIKE = 10.0;
	private static final double K_ORDER = 3.0;


	@Override
	public double calculateScore(long view, long like, long order) {
		return WEIGHT_VIEW * sat(view, K_VIEW)
			+ WEIGHT_LIKE * sat(like, K_LIKE)
			+ WEIGHT_ORDER * sat(order, K_ORDER);
	}


	// saturation 함수: sat(x, k) = x / (x + k), 음수 clamp
	static double sat(long x, double k) {
		if (x <= 0) return 0.0;
		return x / (x + k);
	}

}
