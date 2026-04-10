package com.loopers.batch.job.ranking.scorer;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;


/**
 * 랭킹 Scorer 등록 (commerce-batch)
 * - rebuild 에서 scorerType 파라미터로 선택
 * - Map<String, RankingScorer> 로 조회
 */

@Configuration
public class RankingScorerConfig {

	// sat(x, k) = x / (x + k)
	private static double sat(double x, double k) {
		if (x <= 0) return 0.0;
		return x / (x + k);
	}


	@Bean
	public Map<String, RankingScorer> rankingScorerMap() {
		return Map.of(
			// Saturation 기반 (기본)
			"SATURATION", (view, like, order) ->
				0.15 * sat(view, 100) + 0.35 * sat(like, 10) + 0.50 * sat(order, 3),

			// Linear 정규화
			"LINEAR", (view, like, order) ->
				0.1 * Math.min(1.0, view / 1000.0) +
				0.2 * Math.min(1.0, like / 100.0) +
				0.6 * Math.min(1.0, order / 30.0),

			// Conversion (전환율 기반)
			"CONVERSION", (view, like, order) -> {
				if (view <= 0) return 0.0;
				double orderRate = (double) order / view * 100.0;
				double likeRate = (double) like / view * 100.0;
				double confidence = sat(view, 10);
				return confidence * (0.65 * sat(orderRate, 30) + 0.35 * sat(likeRate, 50));
			}
		);
	}

}
