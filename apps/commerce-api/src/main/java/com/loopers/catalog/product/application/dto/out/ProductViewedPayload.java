package com.loopers.catalog.product.application.dto.out;


import java.time.LocalDateTime;


/**
 * Kafka 발행용 상품 조회 Payload (userId nullable — 비로그인 시 null)
 */
public record ProductViewedPayload(
	Long userId,
	Long productId,
	LocalDateTime occurredAt
) {

	// 팩토리 메서드
	public static ProductViewedPayload of(Long userId, Long productId) {
		return new ProductViewedPayload(userId, productId, LocalDateTime.now());
	}

}
