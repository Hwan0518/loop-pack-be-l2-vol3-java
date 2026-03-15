package com.loopers.catalog.product.application.dto.out;


import java.math.BigDecimal;
import java.time.ZonedDateTime;


/**
 * 상품 관리자 상세 조회 결과 DTO
 * - Read Model projection (QueryDSL)으로 직접 생성
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

}
