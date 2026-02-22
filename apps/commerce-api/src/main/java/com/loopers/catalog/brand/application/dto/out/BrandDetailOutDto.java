package com.loopers.catalog.brand.application.dto.out;


import com.loopers.catalog.brand.domain.model.Brand;


/**
 * 브랜드 상세 조회 결과 DTO
 * - id: 브랜드 ID
 * - name: 브랜드명
 * - description: 브랜드 설명
 */
public record BrandDetailOutDto(Long id, String name, String description) {

	// 1. Brand 도메인 객체를 상세 조회 결과 DTO로 변환
	public static BrandDetailOutDto from(Brand brand) {
		return new BrandDetailOutDto(
			brand.getId(),
			brand.getName().value(),
			brand.getDescription() != null ? brand.getDescription().value() : null
		);
	}

}
