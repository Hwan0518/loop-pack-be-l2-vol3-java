package com.loopers.catalog.brand.interfaces.web.response;


import com.loopers.catalog.brand.application.dto.out.BrandDetailOutDto;


/**
 * 브랜드 상세 응답
 * - id: 브랜드 ID
 * - name: 브랜드명
 * - description: 브랜드 설명
 */
public record BrandDetailResponse(Long id, String name, String description) {

	// 1. BrandDetailOutDto를 컨트롤러 응답 객체로 변환
	public static BrandDetailResponse from(BrandDetailOutDto outDto) {
		return new BrandDetailResponse(outDto.id(), outDto.name(), outDto.description());
	}

}
