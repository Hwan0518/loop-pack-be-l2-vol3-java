package com.loopers.ordering.order.application.dto.out;


import com.loopers.ordering.order.domain.model.Order;

import java.math.BigDecimal;
import java.time.LocalDateTime;


/**
 * 관리자 주문 목록 조회 결과 DTO
 * - id: 주문 ID
 * - userId: 주문자 ID
 * - totalPrice: 주문 총액
 * - itemCount: 주문 항목 수
 * - createdAt: 주문 생성 일시
 */
public record AdminOrderOutDto(
	Long id,
	Long userId,
	BigDecimal totalPrice,
	int itemCount,
	LocalDateTime createdAt
) {

	// 1. Order 도메인 객체를 관리자 DTO로 변환
	public static AdminOrderOutDto from(Order order) {
		return new AdminOrderOutDto(
			order.getId(),
			order.getUserId(),
			order.getTotalPrice(),
			order.getItems().size(),
			order.getCreatedAt()
		);
	}

}
