package com.loopers.catalog.brand.application.dto.in;


import com.loopers.catalog.brand.domain.model.enums.VisibleStatus;


/**
 * 브랜드 수정 요청 DTO
 * - name: 브랜드명
 * - description: 브랜드 설명
 * - visibleStatus: 노출 상태 (nullable, null이면 변경하지 않음)
 */
public record AdminBrandUpdateInDto(String name, String description, VisibleStatus visibleStatus) {
}
