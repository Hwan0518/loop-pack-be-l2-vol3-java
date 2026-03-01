package com.loopers.ordering.order.interfaces.web.response;


import com.loopers.ordering.order.application.dto.out.OrderItemOutDto;

import java.math.BigDecimal;


/**
 * 주문 항목 응답
 * - id: 주문 항목 ID
 * - productId: 상품 ID
 * - snapshotName: 주문 시점 상품명
 * - snapshotPrice: 주문 시점 가격
 * - quantity: 주문 수량
 * - subtotal: 소계
 */
public record OrderItemResponse(
	Long id,
	Long productId,
	String snapshotName,
	BigDecimal snapshotPrice,
	Long quantity,
	BigDecimal subtotal
) {

	// 1. OrderItemOutDto를 응답 객체로 변환
	public static OrderItemResponse from(OrderItemOutDto outDto) {
		return new OrderItemResponse(
			outDto.id(),
			outDto.productId(),
			outDto.snapshotName(),
			outDto.snapshotPrice(),
			outDto.quantity(),
			outDto.subtotal()
		);
	}

}
