package com.loopers.catalog.brand.application.dto.in;


import com.loopers.catalog.brand.domain.model.enums.VisibleStatus;


/**
 * 브랜드 노출 상태 변경 요청 DTO
 * - visibleStatus: 노출 상태
 */
public record AdminBrandVisibleStatusUpdateInDto(VisibleStatus visibleStatus) {
}
