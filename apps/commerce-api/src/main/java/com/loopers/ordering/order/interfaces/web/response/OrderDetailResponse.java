package com.loopers.ordering.order.interfaces.web.response;


import com.loopers.ordering.order.application.dto.out.OrderDetailOutDto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;


/**
 * 주문 상세 조회 응답
 * - id: 주문 ID
 * - userId: 주문자 ID
 * - totalPrice: 주문 총액
 * - items: 주문 항목 목록
 * - createdAt: 주문 생성 일시
 */
public record OrderDetailResponse(
	Long id,
	Long userId,
	BigDecimal totalPrice,
	List<OrderItemResponse> items,
	LocalDateTime createdAt
) {

	// 1. OrderDetailOutDto를 응답 객체로 변환
	public static OrderDetailResponse from(OrderDetailOutDto outDto) {
		return new OrderDetailResponse(
			outDto.id(),
			outDto.userId(),
			outDto.totalPrice(),
			outDto.items().stream().map(OrderItemResponse::from).toList(),
			outDto.createdAt()
		);
	}

}
