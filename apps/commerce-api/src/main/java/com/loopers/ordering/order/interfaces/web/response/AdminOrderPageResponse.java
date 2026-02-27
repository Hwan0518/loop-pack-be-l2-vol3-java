package com.loopers.ordering.order.interfaces.web.response;


import com.loopers.ordering.order.application.dto.out.AdminOrderPageOutDto;

import java.util.List;


/**
 * 관리자 주문 페이지 응답
 * - content: 관리자 주문 목록
 * - page: 페이지 번호 (0-based)
 * - size: 페이지 크기
 * - totalElements: 전체 요소 수
 */
public record AdminOrderPageResponse(List<AdminOrderResponse> content, int page, int size, long totalElements) {

	// 1. AdminOrderPageOutDto를 응답 객체로 변환
	public static AdminOrderPageResponse from(AdminOrderPageOutDto outDto) {
		return new AdminOrderPageResponse(
			outDto.content().stream().map(AdminOrderResponse::from).toList(),
			outDto.page(),
			outDto.size(),
			outDto.totalElements()
		);
	}

}
