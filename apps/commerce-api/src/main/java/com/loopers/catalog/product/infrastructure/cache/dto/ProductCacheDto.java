package com.loopers.catalog.product.infrastructure.cache.dto;


import com.loopers.catalog.product.application.dto.out.ProductDetailOutDto;
import com.loopers.catalog.product.application.dto.out.ProductOutDto;

import java.math.BigDecimal;


/**
 * 상품 캐시 DTO (PLP + PDP 공용)
 * - 상세 캐시 값으로 저장되며, PLP/PDP 응답에 필요한 모든 필드 포함
 * - Read Model에서 직접 projection하여 생성
 *
 * - id: 상품 ID
 * - brandId: 브랜드 ID
 * - brandName: 브랜드명
 * - name: 상품명
 * - price: 가격
 * - stock: 재고
 * - description: 상품 설명
 * - likeCount: 좋아요 수
 */
public record ProductCacheDto(
	Long id, Long brandId, String brandName, String name,
	BigDecimal price, Long stock, String description, Long likeCount
) {

	// 1. PLP 응답용 변환 (description 제외)
	public ProductOutDto toProductOutDto() {
		return new ProductOutDto(id, brandId, brandName, name, price, stock, likeCount);
	}


	// 2. PDP 응답용 변환 (전체 필드)
	public ProductDetailOutDto toProductDetailOutDto() {
		return new ProductDetailOutDto(id, brandId, brandName, name, price, stock, description, likeCount);
	}

}
