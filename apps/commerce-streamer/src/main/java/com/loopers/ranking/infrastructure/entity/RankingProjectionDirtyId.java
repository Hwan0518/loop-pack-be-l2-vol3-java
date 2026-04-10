package com.loopers.ranking.infrastructure.entity;


import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;


/**
 * ranking_projection_dirty 복합 PK 클래스 (JPA @IdClass)
 * - statDate, reason 두 필드
 */
public class RankingProjectionDirtyId implements Serializable {

	private LocalDate statDate;
	private String reason;


	public RankingProjectionDirtyId() {
	}


	public RankingProjectionDirtyId(LocalDate statDate, String reason) {
		this.statDate = statDate;
		this.reason = reason;
	}


	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof RankingProjectionDirtyId that)) return false;
		return Objects.equals(statDate, that.statDate) && Objects.equals(reason, that.reason);
	}


	@Override
	public int hashCode() {
		return Objects.hash(statDate, reason);
	}

}
