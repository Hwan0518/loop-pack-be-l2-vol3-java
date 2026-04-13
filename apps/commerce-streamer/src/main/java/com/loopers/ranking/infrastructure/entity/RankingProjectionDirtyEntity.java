package com.loopers.ranking.infrastructure.entity;


import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;


/**
 * 랭킹 Redis projection 오염 기록 엔티티 (ranking_projection_dirty)
 * - PK: (stat_date, reason)
 * - Redis 쓰기 실패 시 mark → reconcile job 이 resolved_at 갱신
 */

@Entity
@Table(name = "ranking_projection_dirty")
@IdClass(RankingProjectionDirtyId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RankingProjectionDirtyEntity {

	@Id
	@Column(name = "stat_date", nullable = false)
	private LocalDate statDate;

	@Id
	@Column(name = "reason", nullable = false, length = 32)
	private String reason;

	@Column(name = "marked_at", nullable = false)
	private LocalDateTime markedAt;

	@Column(name = "resolved_at")
	private LocalDateTime resolvedAt;

}
