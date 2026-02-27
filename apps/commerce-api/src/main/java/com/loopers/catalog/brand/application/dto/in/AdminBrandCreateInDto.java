package com.loopers.catalog.brand.application.dto.in;


/**
 * 브랜드 생성 요청 DTO
 * - name: 브랜드명
 * - description: 브랜드 설명
 */
public record AdminBrandCreateInDto(String name, String description) {
}
