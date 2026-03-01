package com.loopers.catalog.product.application.dto.in;


import java.math.BigDecimal;


/**
 * 관리자 상품 생성 입력 DTO
 * - brandId: 브랜드 ID
 * - name: 상품명
 * - price: 가격
 * - stock: 재고
 * - description: 상품 설명
 */
public record AdminProductCreateInDto(Long brandId, String name, BigDecimal price, Long stock, String description) {
}
