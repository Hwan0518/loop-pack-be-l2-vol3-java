package com.loopers.ranking.infrastructure.entity;


import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;


/**
 * ranking_daily_score 복합 PK 클래스 (JPA @IdClass)
 * - statDate, scorerType, productId 세 필드 동등성 비교
 */
public class RankingDailyScoreId implements Serializable {

	private LocalDate statDate;
	private String scorerType;
	private Long productId;


	public RankingDailyScoreId() {
	}


	public RankingDailyScoreId(LocalDate statDate, String scorerType, Long productId) {
		this.statDate = statDate;
		this.scorerType = scorerType;
		this.productId = productId;
	}


	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof RankingDailyScoreId that)) return false;
		return Objects.equals(statDate, that.statDate)
			&& Objects.equals(scorerType, that.scorerType)
			&& Objects.equals(productId, that.productId);
	}


	@Override
	public int hashCode() {
		return Objects.hash(statDate, scorerType, productId);
	}

}
