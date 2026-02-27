package com.loopers.ordering.order.application.dto.out;


import com.loopers.ordering.order.domain.model.OrderItem;

import java.math.BigDecimal;


/**
 * 주문 항목 조회 결과 DTO
 * - id: 주문 항목 ID
 * - productId: 상품 ID
 * - snapshotName: 주문 시점 상품명
 * - snapshotPrice: 주문 시점 가격
 * - quantity: 주문 수량
 * - subtotal: 소계
 */
public record OrderItemOutDto(
	Long id,
	Long productId,
	String snapshotName,
	BigDecimal snapshotPrice,
	Long quantity,
	BigDecimal subtotal
) {

	// 1. OrderItem 도메인 객체를 DTO로 변환
	public static OrderItemOutDto from(OrderItem orderItem) {
		return new OrderItemOutDto(
			orderItem.getId(),
			orderItem.getProductId(),
			orderItem.getSnapshotName().value(),
			orderItem.getSnapshotPrice().value(),
			orderItem.getQuantity(),
			orderItem.getSubtotal()
		);
	}

}
