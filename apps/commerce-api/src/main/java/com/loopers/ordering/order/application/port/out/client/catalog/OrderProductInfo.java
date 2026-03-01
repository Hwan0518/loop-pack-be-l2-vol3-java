package com.loopers.ordering.order.application.port.out.client.catalog;


import java.math.BigDecimal;


/**
 * 주문용 상품 정보
 * - productId: 상품 ID
 * - name: 상품명
 * - price: 상품 가격
 * - stock: 재고 수량
 */
public record OrderProductInfo(Long productId, String name, BigDecimal price, Long stock) {
}
