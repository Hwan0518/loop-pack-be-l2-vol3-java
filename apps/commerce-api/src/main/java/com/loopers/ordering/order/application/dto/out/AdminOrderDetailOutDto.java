package com.loopers.ordering.order.application.dto.out;


import com.loopers.ordering.order.domain.model.Order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;


/**
 * 관리자 주문 상세 조회 결과 DTO
 * - id: 주문 ID
 * - userId: 주문자 ID
 * - totalPrice: 주문 총액
 * - items: 주문 항목 목록
 * - createdAt: 주문 생성 일시
 */
public record AdminOrderDetailOutDto(
	Long id,
	Long userId,
	BigDecimal totalPrice,
	List<OrderItemOutDto> items,
	LocalDateTime createdAt
) {

	// 1. Order 도메인 객체를 관리자 상세 DTO로 변환
	public static AdminOrderDetailOutDto from(Order order) {
		return new AdminOrderDetailOutDto(
			order.getId(),
			order.getUserId(),
			order.getTotalPrice(),
			order.getItems().stream().map(OrderItemOutDto::from).toList(),
			order.getCreatedAt()
		);
	}

}
