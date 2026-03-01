package com.loopers.catalog.product.interfaces.web.response;


import com.loopers.catalog.product.application.dto.out.AdminProductPageOutDto;

import java.util.List;


/**
 * 상품 관리자 페이지 응답
 * - content: 상품 관리자 목록
 * - page: 페이지 번호 (0-based)
 * - size: 페이지 크기
 * - totalElements: 전체 요소 수
 */
public record AdminProductPageResponse(List<AdminProductResponse> content, int page, int size, long totalElements) {

	// 1. AdminProductPageOutDto를 컨트롤러 응답 객체로 변환
	public static AdminProductPageResponse from(AdminProductPageOutDto outDto) {
		return new AdminProductPageResponse(
			outDto.content().stream().map(AdminProductResponse::from).toList(),
			outDto.page(),
			outDto.size(),
			outDto.totalElements()
		);
	}

}
