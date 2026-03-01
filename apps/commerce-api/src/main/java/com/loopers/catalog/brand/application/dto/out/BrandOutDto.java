package com.loopers.catalog.brand.application.dto.out;


import com.loopers.catalog.brand.domain.model.Brand;


/**
 * 브랜드 목록 조회 결과 DTO
 * - id: 브랜드 ID
 * - name: 브랜드명
 */
public record BrandOutDto(Long id, String name) {

	// 1. Brand 도메인 객체를 목록 조회 결과 DTO로 변환
	public static BrandOutDto from(Brand brand) {
		return new BrandOutDto(brand.getId(), brand.getName().value());
	}

}
