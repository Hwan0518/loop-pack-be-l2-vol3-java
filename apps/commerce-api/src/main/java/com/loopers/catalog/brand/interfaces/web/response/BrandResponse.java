package com.loopers.catalog.brand.interfaces.web.response;


import com.loopers.catalog.brand.application.dto.out.BrandOutDto;


/**
 * 브랜드 목록 응답
 * - id: 브랜드 ID
 * - name: 브랜드명
 */
public record BrandResponse(Long id, String name) {

	// 1. BrandOutDto를 컨트롤러 응답 객체로 변환
	public static BrandResponse from(BrandOutDto outDto) {
		return new BrandResponse(outDto.id(), outDto.name());
	}

}
