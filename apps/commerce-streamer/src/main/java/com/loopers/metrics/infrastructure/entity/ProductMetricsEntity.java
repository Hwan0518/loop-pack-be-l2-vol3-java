package com.loopers.metrics.infrastructure.entity;


import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;


/**
 * 상품 메트릭 엔티티 (product_metrics — SoT)
 * - MetricsCollectorConsumer가 delta 반영 + version 증가
 */

@Entity
@Table(name = "product_metrics")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductMetricsEntity {

	@Id
	private Long productId;

	@Column(nullable = false)
	private Long likeCount;

	@Column(nullable = false)
	private Long salesCount;

	@Column(nullable = false)
	private Long viewCount;

	@Column(nullable = false)
	private Long version;

	@Column(nullable = false)
	private LocalDateTime updatedAt;


	// 신규 생성 (upsert 시 존재하지 않을 때)
	public static ProductMetricsEntity createDefault(Long productId) {
		ProductMetricsEntity entity = new ProductMetricsEntity();
		entity.productId = productId;
		entity.likeCount = 0L;
		entity.salesCount = 0L;
		entity.viewCount = 0L;
		entity.version = 0L;
		entity.updatedAt = LocalDateTime.now();
		return entity;
	}


	// delta 적용 + version 증가
	public void applyDelta(long likeDelta, long salesDelta, long viewDelta) {
		this.likeCount = Math.max(0, this.likeCount + likeDelta);
		this.salesCount = Math.max(0, this.salesCount + salesDelta);
		this.viewCount = Math.max(0, this.viewCount + viewDelta);
		this.version++;
		this.updatedAt = LocalDateTime.now();
	}

}
