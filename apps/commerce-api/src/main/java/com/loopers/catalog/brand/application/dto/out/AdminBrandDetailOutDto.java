package com.loopers.catalog.brand.application.dto.out;


import com.loopers.catalog.brand.domain.model.Brand;
import com.loopers.catalog.brand.domain.model.enums.VisibleStatus;


/**
 * 브랜드 관리자 상세 조회 결과 DTO
 * - id: 브랜드 ID
 * - name: 브랜드명
 * - description: 브랜드 설명
 * - visibleStatus: 노출 상태
 */
public record AdminBrandDetailOutDto(Long id, String name, String description, VisibleStatus visibleStatus) {

	// 1. Brand 도메인 객체를 관리자 상세 조회 결과 DTO로 변환
	public static AdminBrandDetailOutDto from(Brand brand) {
		return new AdminBrandDetailOutDto(
			brand.getId(),
			brand.getName().value(),
			brand.getDescription() != null ? brand.getDescription().value() : null,
			brand.getVisibleStatus()
		);
	}

}
