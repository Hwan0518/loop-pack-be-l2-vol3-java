package com.loopers.catalog.product.application.dto.out;


import com.loopers.catalog.product.domain.repository.vo.PageResult;

import java.util.List;


/**
 * 상품 페이지 조회 결과 DTO
 * - content: 상품 목록
 * - page: 페이지 번호 (0-based)
 * - size: 페이지 크기
 * - totalElements: 전체 요소 수
 */
public record ProductPageOutDto(List<ProductOutDto> content, int page, int size, long totalElements) {

	// 1. PageResult<ProductOutDto>를 페이지 조회 결과 DTO로 변환
	public static ProductPageOutDto from(PageResult<ProductOutDto> pageResult) {
		return new ProductPageOutDto(
			pageResult.content(),
			pageResult.page(),
			pageResult.size(),
			pageResult.totalElements()
		);
	}

}
