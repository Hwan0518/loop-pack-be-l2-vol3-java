package com.loopers.ranking.infrastructure.entity;


import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;


/**
 * 랭킹 일간 카운터 엔티티 (ranking_daily_counter — Scorer 재계산용 재료 SoT)
 * - PK: (stat_date, product_id)
 * - view/like/order 카운트를 raw signed 값으로 저장 (음수 허용)
 * - clamp는 scorer 입력 시점에만 적용
 * - 갱신 주체: ranking-collector consumer
 */

@Entity
@Table(name = "ranking_daily_counter", indexes = {
	@Index(name = "idx_ranking_daily_counter_product", columnList = "product_id, stat_date")
})
@IdClass(RankingDailyCounterId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RankingDailyCounterEntity {

	@Id
	@Column(name = "stat_date", nullable = false)
	private LocalDate statDate;

	@Id
	@Column(name = "product_id", nullable = false)
	private Long productId;

	@Column(name = "view_count", nullable = false)
	private Long viewCount;

	@Column(name = "like_count", nullable = false)
	private Long likeCount;

	@Column(name = "order_qty", nullable = false)
	private Long orderQty;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

}
