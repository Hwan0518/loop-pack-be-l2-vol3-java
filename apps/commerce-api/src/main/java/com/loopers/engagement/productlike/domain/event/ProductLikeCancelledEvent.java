package com.loopers.engagement.productlike.domain.event;

/**
 * 상품 좋아요 취소 이벤트
 * - productId: 좋아요 취소 대상 상품 ID
 */
public record ProductLikeCancelledEvent(Long productId) {
}
