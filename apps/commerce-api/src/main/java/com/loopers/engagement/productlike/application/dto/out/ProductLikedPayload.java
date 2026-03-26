package com.loopers.engagement.productlike.application.dto.out;


import com.loopers.engagement.productlike.domain.model.ProductLike;

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

	// 팩토리 메서드 — ProductLike 도메인 모델로 생성
	public static ProductLikedPayload from(ProductLike like) {
		return new ProductLikedPayload(like.getId(), like.getUserId(), like.getTargetId(), LocalDateTime.now());
	}

}
