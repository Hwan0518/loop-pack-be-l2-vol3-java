package com.loopers.catalog.brand.interfaces.web.response;


import com.loopers.catalog.brand.application.dto.out.BrandAdminPageOutDto;

import java.util.List;


/**
 * 브랜드 관리자 페이지 응답
 * - content: 브랜드 관리자 목록
 * - page: 페이지 번호 (0-based)
 * - size: 페이지 크기
 * - totalElements: 전체 요소 수
 */
public record BrandAdminPageResponse(List<BrandAdminResponse> content, int page, int size, long totalElements) {

	// 1. BrandAdminPageOutDto를 컨트롤러 응답 객체로 변환
	public static BrandAdminPageResponse from(BrandAdminPageOutDto outDto) {
		return new BrandAdminPageResponse(
			outDto.content().stream().map(BrandAdminResponse::from).toList(),
			outDto.page(),
			outDto.size(),
			outDto.totalElements()
		);
	}

}
