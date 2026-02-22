package com.loopers.catalog.brand.application.dto.out;


import com.loopers.catalog.brand.domain.model.Brand;
import com.loopers.catalog.brand.domain.model.enums.VisibleStatus;


/**
 * 브랜드 관리자 목록 조회 결과 DTO
 * - id: 브랜드 ID
 * - name: 브랜드명
 * - visibleStatus: 노출 상태
 */
public record BrandAdminOutDto(Long id, String name, VisibleStatus visibleStatus) {

	// 1. Brand 도메인 객체를 관리자 목록 조회 결과 DTO로 변환
	public static BrandAdminOutDto from(Brand brand) {
		return new BrandAdminOutDto(brand.getId(), brand.getName().value(), brand.getVisibleStatus());
	}

}
