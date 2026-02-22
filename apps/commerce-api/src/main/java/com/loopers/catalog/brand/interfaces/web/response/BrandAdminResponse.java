package com.loopers.catalog.brand.interfaces.web.response;


import com.loopers.catalog.brand.application.dto.out.BrandAdminOutDto;
import com.loopers.catalog.brand.domain.model.enums.VisibleStatus;


/**
 * 브랜드 관리자 목록 응답
 * - id: 브랜드 ID
 * - name: 브랜드명
 * - visibleStatus: 노출 상태
 */
public record BrandAdminResponse(Long id, String name, VisibleStatus visibleStatus) {

	// 1. BrandAdminOutDto를 컨트롤러 응답 객체로 변환
	public static BrandAdminResponse from(BrandAdminOutDto outDto) {
		return new BrandAdminResponse(outDto.id(), outDto.name(), outDto.visibleStatus());
	}

}
