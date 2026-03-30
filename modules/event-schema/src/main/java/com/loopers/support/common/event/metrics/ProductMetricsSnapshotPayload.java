package com.loopers.support.common.event.metrics;


/**
 * Kafka 발행용 상품 메트릭 스냅샷 Payload
 */
public record ProductMetricsSnapshotPayload(
	Long productId,
	Long likeCount,
	Long salesCount,
	Long viewCount,
	Long version,
	String updatedAt
) {

	// 팩토리 메서드
	public static ProductMetricsSnapshotPayload of(Long productId, Long likeCount,
		Long salesCount, Long viewCount, Long version, String updatedAt) {
		return new ProductMetricsSnapshotPayload(productId, likeCount, salesCount,
			viewCount, version, updatedAt);
	}

}
