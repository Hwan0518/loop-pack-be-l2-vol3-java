package com.loopers.catalog.brand.interfaces.web.response;


import com.loopers.catalog.brand.application.dto.out.BrandPageOutDto;

import java.util.List;


/**
 * 브랜드 페이지 응답
 * - content: 브랜드 목록
 * - page: 페이지 번호 (0-based)
 * - size: 페이지 크기
 * - totalElements: 전체 요소 수
 */
public record BrandPageResponse(List<BrandResponse> content, int page, int size, long totalElements) {

	// 1. BrandPageOutDto를 컨트롤러 응답 객체로 변환
	public static BrandPageResponse from(BrandPageOutDto outDto) {
		return new BrandPageResponse(
			outDto.content().stream().map(BrandResponse::from).toList(),
			outDto.page(),
			outDto.size(),
			outDto.totalElements()
		);
	}

}
