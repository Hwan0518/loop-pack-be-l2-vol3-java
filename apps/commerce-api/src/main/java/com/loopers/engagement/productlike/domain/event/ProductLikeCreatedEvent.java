package com.loopers.engagement.productlike.domain.event;

/**
 * 상품 좋아요 생성 이벤트
 * - productId: 좋아요 대상 상품 ID
 */
public record ProductLikeCreatedEvent(Long productId) {
}
