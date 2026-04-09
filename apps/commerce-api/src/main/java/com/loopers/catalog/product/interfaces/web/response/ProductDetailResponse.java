package com.loopers.catalog.product.interfaces.web.response;


import com.loopers.catalog.product.application.dto.out.ProductDetailOutDto;

import java.math.BigDecimal;


/**
 * 상품 상세 응답
 * - id: 상품 ID
 * - brandId: 브랜드 ID
 * - brandName: 브랜드명
 * - name: 상품명
 * - price: 가격
 * - stock: 재고
 * - description: 상품 설명
 * - likeCount: 좋아요 수
 * - rank: 오늘의 랭킹 순위 (1-based, 미등록이면 null)
 */
public record ProductDetailResponse(Long id, Long brandId, String brandName, String name,
	BigDecimal price, Long stock, String description, Long likeCount, Long rank) {

	// 1. ProductDetailOutDto를 컨트롤러 응답 객체로 변환
	public static ProductDetailResponse from(ProductDetailOutDto outDto) {
		return new ProductDetailResponse(
			outDto.id(), outDto.brandId(), outDto.brandName(), outDto.name(),
			outDto.price(), outDto.stock(), outDto.description(), outDto.likeCount(), outDto.rank()
		);
	}

}
