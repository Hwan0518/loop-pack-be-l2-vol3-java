package com.loopers.catalog.product.application.dto.out;


import com.loopers.catalog.product.domain.repository.vo.PageResult;

import java.util.List;


/**
 * 상품 관리자 페이지 조회 결과 DTO
 * - content: 상품 관리자 목록
 * - page: 페이지 번호 (0-based)
 * - size: 페이지 크기
 * - totalElements: 전체 요소 수
 */
public record AdminProductPageOutDto(List<AdminProductOutDto> content, int page, int size, long totalElements) {

	// 1. PageResult<AdminProductOutDto>를 관리자 페이지 조회 결과 DTO로 변환
	public static AdminProductPageOutDto from(PageResult<AdminProductOutDto> pageResult) {
		return new AdminProductPageOutDto(
			pageResult.content(),
			pageResult.page(),
			pageResult.size(),
			pageResult.totalElements()
		);
	}

}
