package com.loopers.ranking.infrastructure.entity;


import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;


/**
 * 랭킹 일간 점수 스냅샷 엔티티 (ranking_daily_score)
 * - PK: (stat_date, scorer_type, product_id)
 * - organic_score: 오늘 counter 로 계산한 점수 (consumer 가 매 batch 마다 재계산하여 덮어씀)
 * - carry_score:   전날 (organic + carry) * weight 로 산정된 carry-over (carry-over 스케줄러 전용)
 * - 최종 score    = organic_score + carry_score
 * - consumer 는 organic_score 만, carry-over 스케줄러는 carry_score 만 갱신한다 (책임 분리)
 */

@Entity
@Table(name = "ranking_daily_score", indexes = {
	@Index(name = "idx_ranking_daily_score_product", columnList = "product_id, stat_date")
})
@IdClass(RankingDailyScoreId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RankingDailyScoreEntity {

	@Id
	@Column(name = "stat_date", nullable = false)
	private LocalDate statDate;

	@Id
	@Column(name = "scorer_type", nullable = false, length = 32)
	private String scorerType;

	@Id
	@Column(name = "product_id", nullable = false)
	private Long productId;

	@Column(name = "organic_score", nullable = false)
	private Double organicScore;

	@Column(name = "carry_score", nullable = false)
	private Double carryScore;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

}
