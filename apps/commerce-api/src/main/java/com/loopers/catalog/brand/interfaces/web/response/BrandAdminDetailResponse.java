package com.loopers.catalog.brand.interfaces.web.response;


import com.loopers.catalog.brand.application.dto.out.BrandAdminDetailOutDto;
import com.loopers.catalog.brand.domain.model.enums.VisibleStatus;


/**
 * 브랜드 관리자 상세 응답
 * - id: 브랜드 ID
 * - name: 브랜드명
 * - description: 브랜드 설명
 * - visibleStatus: 노출 상태
 */
public record BrandAdminDetailResponse(Long id, String name, String description, VisibleStatus visibleStatus) {

	// 1. BrandAdminDetailOutDto를 컨트롤러 응답 객체로 변환
	public static BrandAdminDetailResponse from(BrandAdminDetailOutDto outDto) {
		return new BrandAdminDetailResponse(outDto.id(), outDto.name(), outDto.description(), outDto.visibleStatus());
	}

}
