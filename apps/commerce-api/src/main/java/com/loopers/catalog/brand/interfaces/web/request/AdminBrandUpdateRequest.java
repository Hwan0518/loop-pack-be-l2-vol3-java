package com.loopers.catalog.brand.interfaces.web.request;


import com.loopers.catalog.brand.domain.model.enums.VisibleStatus;
import jakarta.validation.constraints.NotBlank;


/**
 * 브랜드 수정 요청
 * - name: 브랜드명 (필수)
 * - description: 브랜드 설명 (선택)
 * - visibleStatus: 노출 상태 (선택, null이면 변경하지 않음)
 */
public record AdminBrandUpdateRequest(
	@NotBlank(message = "브랜드명은 필수입니다.")
	String name,

	String description,

	VisibleStatus visibleStatus
) {
}
