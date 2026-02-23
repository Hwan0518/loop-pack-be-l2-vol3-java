package com.loopers.catalog.product.domain.event;


/**
 * 상품 삭제 이벤트
 * - productId: 삭제된 상품 ID
 */
public record ProductDeletedEvent(Long productId) {
}
