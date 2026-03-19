package com.loopers.ordering.order.interfaces.web.response;


import com.loopers.ordering.order.application.dto.out.AdminOrderDetailOutDto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;


/**
 * 관리자 주문 상세 조회 응답
 * - id: 주문 ID
 * - userId: 주문자 ID
 * - totalPrice: 주문 총액
 * - status: 주문 상태
 * - items: 주문 항목 목록
 * - createdAt: 주문 생성 일시
 */
public record AdminOrderDetailResponse(
	Long id,
	Long userId,
	BigDecimal totalPrice,
	String status,
	List<OrderItemResponse> items,
	LocalDateTime createdAt
) {

	// 1. AdminOrderDetailOutDto를 응답 객체로 변환
	public static AdminOrderDetailResponse from(AdminOrderDetailOutDto outDto) {
		return new AdminOrderDetailResponse(
			outDto.id(),
			outDto.userId(),
			outDto.totalPrice(),
			outDto.status().name(),
			outDto.items().stream().map(OrderItemResponse::from).toList(),
			outDto.createdAt()
		);
	}

}
