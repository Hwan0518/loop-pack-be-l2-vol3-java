package com.loopers.ordering.order.application.port.out.client.cart;


/**
 * 주문용 장바구니 항목 정보
 * - cartItemId: 장바구니 항목 ID
 * - productId: 상품 ID
 * - quantity: 수량
 */
public record OrderCartItemInfo(Long cartItemId, Long productId, Long quantity) {
}
