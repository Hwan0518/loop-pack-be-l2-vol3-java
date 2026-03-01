package com.loopers.catalog.product.application.dto.out;


import com.loopers.catalog.product.domain.model.Product;

import java.math.BigDecimal;


/**
 * 상품 목록 조회 결과 DTO
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

	// 1. Product 도메인 객체를 목록 조회 결과 DTO로 변환
	public static ProductOutDto from(Product product) {
		return new ProductOutDto(
			product.getId(),
			product.getBrandId(),
			null,
			product.getName().value(),
			product.getPrice().value(),
			product.getStock().value(),
			product.getLikeCount()
		);
	}


	// 2. Product 도메인 객체를 브랜드명 포함 목록 조회 결과 DTO로 변환
	public static ProductOutDto from(Product product, String brandName) {
		return new ProductOutDto(
			product.getId(),
			product.getBrandId(),
			brandName,
			product.getName().value(),
			product.getPrice().value(),
			product.getStock().value(),
			product.getLikeCount()
		);
	}

}
