package com.loopers.catalog.product.application.dto.out;


import com.loopers.catalog.product.domain.model.Product;

import java.math.BigDecimal;
import java.time.ZonedDateTime;


/**
 * 상품 관리자 목록 조회 결과 DTO
 * - id: 상품 ID
 * - brandId: 브랜드 ID
 * - brandName: 브랜드명
 * - name: 상품명
 * - price: 가격
 * - stock: 재고
 * - likeCount: 좋아요 수
 * - deletedAt: 삭제 일시
 */
public record AdminProductOutDto(Long id, Long brandId, String brandName, String name,
	BigDecimal price, Long stock, Long likeCount, ZonedDateTime deletedAt) {

	// 1. Product 도메인 객체를 관리자 목록 조회 결과 DTO로 변환
	public static AdminProductOutDto from(Product product) {
		return new AdminProductOutDto(
			product.getId(),
			product.getBrandId(),
			null,
			product.getName().value(),
			product.getPrice().value(),
			product.getStock().value(),
			product.getLikeCount(),
			product.getDeletedAt()
		);
	}


	// 2. Product 도메인 객체를 브랜드명 포함 관리자 목록 조회 결과 DTO로 변환
	public static AdminProductOutDto from(Product product, String brandName) {
		return new AdminProductOutDto(
			product.getId(),
			product.getBrandId(),
			brandName,
			product.getName().value(),
			product.getPrice().value(),
			product.getStock().value(),
			product.getLikeCount(),
			product.getDeletedAt()
		);
	}

}
