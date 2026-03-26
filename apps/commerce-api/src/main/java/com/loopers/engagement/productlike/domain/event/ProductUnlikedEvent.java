package com.loopers.engagement.productlike.domain.event;


import java.time.LocalDateTime;


/**
 * 상품 좋아요 삭제 이벤트
 * @subscriber ProductLikeEventListener - likeCount 감소 (eventual consistency)
 */
public record ProductUnlikedEvent(
	Long userId,
	Long productId,
	LocalDateTime occurredAt
) {

	// 팩토리 메서드 — userId + productId로 생성
	public static ProductUnlikedEvent of(Long userId, Long productId) {
		return new ProductUnlikedEvent(userId, productId, LocalDateTime.now());
	}

}
