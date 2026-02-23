package com.loopers.catalog.product.interfaces.web.response;


import com.loopers.catalog.product.application.dto.out.AdminProductDetailOutDto;

import java.math.BigDecimal;
import java.time.ZonedDateTime;


/**
 * 상품 관리자 상세 응답
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
public record AdminProductDetailResponse(Long id, Long brandId, String brandName, String name,
	BigDecimal price, Long stock, String description, Long likeCount, ZonedDateTime deletedAt) {

	// 1. AdminProductDetailOutDto를 컨트롤러 응답 객체로 변환
	public static AdminProductDetailResponse from(AdminProductDetailOutDto outDto) {
		return new AdminProductDetailResponse(
			outDto.id(), outDto.brandId(), outDto.brandName(), outDto.name(),
			outDto.price(), outDto.stock(), outDto.description(), outDto.likeCount(), outDto.deletedAt()
		);
	}

}
