package com.loopers.catalog.brand.interfaces.web.request;


import jakarta.validation.constraints.NotBlank;


/**
 * 브랜드 생성 요청
 * - name: 브랜드명 (필수)
 * - description: 브랜드 설명 (선택)
 */
public record AdminBrandCreateRequest(
	@NotBlank(message = "브랜드명은 필수입니다.")
	String name,

	String description
) {
}
