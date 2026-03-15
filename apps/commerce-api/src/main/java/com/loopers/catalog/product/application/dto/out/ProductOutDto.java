package com.loopers.catalog.product.application.dto.out;


import java.math.BigDecimal;


/**
 * 상품 목록 조회 결과 DTO
 * - 캐시(ProductCacheDto) 또는 Read Model projection(QueryDSL)으로 직접 생성
 * - id: 상품 ID
 * - brandId: 브랜드 ID
 * - brandName: 브랜드명
 * - name: 상품명
 * - price: 가격
 * - stock: 재고
 * - likeCount: 좋아요 수
 */
public record ProductOutDto(Long id, Long brandId, String brandName, String name,
	BigDecimal price, Long stock, Long likeCount) {

}
