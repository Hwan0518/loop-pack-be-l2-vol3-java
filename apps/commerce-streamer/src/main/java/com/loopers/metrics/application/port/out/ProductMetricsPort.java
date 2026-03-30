package com.loopers.metrics.application.port.out;


/**
 * 상품 메트릭 관리 포트 (application → infrastructure 계약)
 * - delta 반영 (upsert) + snapshot 데이터 반환
 */
public interface ProductMetricsPort {

	/**
	 * delta 적용 후 최신 snapshot 반환
	 * @return MetricsSnapshot (productId, likeCount, salesCount, viewCount, version, updatedAt)
	 */
	MetricsSnapshot applyDeltaAndGet(Long productId, long likeDelta, long salesDelta, long viewDelta);


	/**
	 * 메트릭 snapshot DTO
	 */
	record MetricsSnapshot(
		Long productId,
		Long likeCount,
		Long salesCount,
		Long viewCount,
		Long version,
		String updatedAt
	) {
	}

}
