package com.loopers.catalog.product.interfaces.web.response;


import com.loopers.catalog.product.application.dto.out.ProductOutDto;

import java.math.BigDecimal;


/**
 * 상품 목록 응답
 * - id: 상품 ID
 * - brandId: 브랜드 ID
 * - brandName: 브랜드명
 * - name: 상품명
 * - price: 가격
 * - stock: 재고
 * - likeCount: 좋아요 수
 */
public record ProductResponse(Long id, Long brandId, String brandName, String name,
	BigDecimal price, Long stock, Long likeCount) {

	// 1. ProductOutDto를 컨트롤러 응답 객체로 변환
	public static ProductResponse from(ProductOutDto outDto) {
		return new ProductResponse(
			outDto.id(), outDto.brandId(), outDto.brandName(), outDto.name(),
			outDto.price(), outDto.stock(), outDto.likeCount()
		);
	}

}
