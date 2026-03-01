package com.loopers.ordering.order.interfaces.web.response;


import com.loopers.ordering.order.application.dto.out.AdminOrderOutDto;

import java.math.BigDecimal;
import java.time.LocalDateTime;


/**
 * 관리자 주문 목록 조회 응답
 * - id: 주문 ID
 * - userId: 주문자 ID
 * - totalPrice: 주문 총액
 * - itemCount: 주문 항목 수
 * - createdAt: 주문 생성 일시
 */
public record AdminOrderResponse(
	Long id,
	Long userId,
	BigDecimal totalPrice,
	int itemCount,
	LocalDateTime createdAt
) {

	// 1. AdminOrderOutDto를 응답 객체로 변환
	public static AdminOrderResponse from(AdminOrderOutDto outDto) {
		return new AdminOrderResponse(
			outDto.id(),
			outDto.userId(),
			outDto.totalPrice(),
			outDto.itemCount(),
			outDto.createdAt()
		);
	}

}
