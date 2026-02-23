package com.loopers.catalog.product.interfaces.web.request;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;


/**
 * 관리자 상품 생성 요청
 * - brandId: 브랜드 ID (필수)
 * - name: 상품명 (필수)
 * - price: 가격 (필수)
 * - stock: 재고 (필수)
 * - description: 상품 설명 (선택)
 */
public record AdminProductCreateRequest(
	@NotNull Long brandId,
	@NotBlank String name,
	@NotNull BigDecimal price,
	@NotNull Long stock,
	String description
) {
}
