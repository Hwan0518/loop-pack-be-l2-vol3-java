package com.loopers.catalog.product.application.dto.out;


import java.math.BigDecimal;


/**
 * 상품 상세 조회 결과 DTO
 * - 캐시(ProductCacheDto) 또는 Read Model projection으로 직접 생성
 * - id: 상품 ID
 * - brandId: 브랜드 ID
 * - brandName: 브랜드명
 * - name: 상품명
 * - price: 가격
 * - stock: 재고
 * - description: 상품 설명
 * - likeCount: 좋아요 수
 */
public record ProductDetailOutDto(Long id, Long brandId, String brandName, String name,
	BigDecimal price, Long stock, String description, Long likeCount) {

}
