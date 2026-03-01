package com.loopers.ordering.order.interfaces.web.response;


import com.loopers.ordering.order.application.dto.out.OrderPageOutDto;

import java.util.List;


/**
 * 주문 페이지 응답
 * - content: 주문 목록
 * - page: 페이지 번호 (0-based)
 * - size: 페이지 크기
 * - totalElements: 전체 요소 수
 */
public record OrderPageResponse(List<OrderResponse> content, int page, int size, long totalElements) {

	// 1. OrderPageOutDto를 응답 객체로 변환
	public static OrderPageResponse from(OrderPageOutDto outDto) {
		return new OrderPageResponse(
			outDto.content().stream().map(OrderResponse::from).toList(),
			outDto.page(),
			outDto.size(),
			outDto.totalElements()
		);
	}

}
