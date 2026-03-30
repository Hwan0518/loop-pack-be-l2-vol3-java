package com.loopers.support.common.event.catalog;


import java.time.LocalDateTime;


/**
 * Kafka 발행용 좋아요 생성 Payload
 */
public record ProductLikedPayload(
	Long productLikeId,
	Long userId,
	Long productId,
	LocalDateTime occurredAt
) {

	// 팩토리 메서드 — 원시값으로 생성 (도메인 비의존)
	public static ProductLikedPayload of(Long productLikeId, Long userId, Long productId) {
		return new ProductLikedPayload(productLikeId, userId, productId, LocalDateTime.now());
	}

}
