package com.loopers.ranking.infrastructure.entity;


import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;


/**
 * ranking_daily_counter 복합 PK 클래스 (JPA @IdClass)
 * - statDate, productId 두 필드만으로 동등성 비교
 */
public class RankingDailyCounterId implements Serializable {

	private LocalDate statDate;
	private Long productId;


	public RankingDailyCounterId() {
	}


	public RankingDailyCounterId(LocalDate statDate, Long productId) {
		this.statDate = statDate;
		this.productId = productId;
	}


	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof RankingDailyCounterId that)) return false;
		return Objects.equals(statDate, that.statDate) && Objects.equals(productId, that.productId);
	}


	@Override
	public int hashCode() {
		return Objects.hash(statDate, productId);
	}

}
