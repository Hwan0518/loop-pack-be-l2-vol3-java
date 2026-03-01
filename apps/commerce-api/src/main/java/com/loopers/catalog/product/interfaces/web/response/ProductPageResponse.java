package com.loopers.catalog.product.interfaces.web.response;


import com.loopers.catalog.product.application.dto.out.ProductPageOutDto;

import java.util.List;


/**
 * 상품 페이지 응답
 * - content: 상품 목록
 * - page: 페이지 번호 (0-based)
 * - size: 페이지 크기
 * - totalElements: 전체 요소 수
 */
public record ProductPageResponse(List<ProductResponse> content, int page, int size, long totalElements) {

	// 1. ProductPageOutDto를 컨트롤러 응답 객체로 변환
	public static ProductPageResponse from(ProductPageOutDto outDto) {
		return new ProductPageResponse(
			outDto.content().stream().map(ProductResponse::from).toList(),
			outDto.page(),
			outDto.size(),
			outDto.totalElements()
		);
	}

}
