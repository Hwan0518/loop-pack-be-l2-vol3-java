package com.loopers.catalog.product.application.dto.out;


import com.loopers.catalog.product.domain.model.Product;

import java.math.BigDecimal;
import java.time.ZonedDateTime;


/**
 * 상품 관리자 상세 조회 결과 DTO
 * - id: 상품 ID
 * - brandId: 브랜드 ID
 * - brandName: 브랜드명
 * - name: 상품명
 * - price: 가격
 * - stock: 재고
 * - description: 상품 설명
 * - likeCount: 좋아요 수
 * - deletedAt: 삭제 일시
 */
public record AdminProductDetailOutDto(Long id, Long brandId, String brandName, String name,
	BigDecimal price, Long stock, String description, Long likeCount, ZonedDateTime deletedAt) {

	// 1. Product 도메인 객체를 브랜드명 포함 관리자 상세 조회 결과 DTO로 변환
	public static AdminProductDetailOutDto from(Product product, String brandName) {
		return new AdminProductDetailOutDto(
			product.getId(),
			product.getBrandId(),
			brandName,
			product.getName().value(),
			product.getPrice().value(),
			product.getStock().value(),
			product.getDescription() != null ? product.getDescription().value() : null,
			product.getLikeCount(),
			product.getDeletedAt()
		);
	}

}
