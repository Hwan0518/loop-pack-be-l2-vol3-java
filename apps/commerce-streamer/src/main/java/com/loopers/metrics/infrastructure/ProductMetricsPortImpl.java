package com.loopers.metrics.infrastructure;


import com.loopers.metrics.application.port.out.ProductMetricsPort;
import com.loopers.metrics.infrastructure.entity.ProductMetricsEntity;
import com.loopers.metrics.infrastructure.jpa.ProductMetricsJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;


/**
 * 상품 메트릭 관리 포트 구현체
 * - ProductMetricsJpaRepository 위임
 */

@Repository
@RequiredArgsConstructor
public class ProductMetricsPortImpl implements ProductMetricsPort {

	// jpa
	private final ProductMetricsJpaRepository productMetricsJpaRepository;


	// 1. delta 적용 후 최신 snapshot 반환 (upsert)
	@Override
	public MetricsSnapshot applyDeltaAndGet(Long productId, long likeDelta, long salesDelta, long viewDelta) {
		// upsert: 존재하면 delta 적용, 없으면 신규 생성
		ProductMetricsEntity metrics = productMetricsJpaRepository.findById(productId)
			.orElseGet(() -> ProductMetricsEntity.createDefault(productId));

		metrics.applyDelta(likeDelta, salesDelta, viewDelta);
		productMetricsJpaRepository.save(metrics);

		return new MetricsSnapshot(
			metrics.getProductId(),
			metrics.getLikeCount(),
			metrics.getSalesCount(),
			metrics.getViewCount(),
			metrics.getVersion(),
			metrics.getUpdatedAt().toString()
		);
	}

}
