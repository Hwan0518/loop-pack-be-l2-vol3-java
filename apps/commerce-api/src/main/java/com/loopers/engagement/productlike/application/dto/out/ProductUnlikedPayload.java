package com.loopers.engagement.productlike.application.dto.out;


import java.time.LocalDateTime;


/**
 * Kafka 발행용 좋아요 삭제 Payload
 */
public record ProductUnlikedPayload(
	Long userId,
	Long productId,
	LocalDateTime occurredAt
) {

	// 팩토리 메서드
	public static ProductUnlikedPayload of(Long userId, Long productId) {
		return new ProductUnlikedPayload(userId, productId, LocalDateTime.now());
	}

}
