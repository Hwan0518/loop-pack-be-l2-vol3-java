package com.loopers.catalog.brand.interfaces.web.request;


import com.loopers.catalog.brand.domain.model.enums.VisibleStatus;
import jakarta.validation.constraints.NotNull;


/**
 * 브랜드 노출 상태 변경 요청
 * - visibleStatus: 노출 상태 (필수)
 */
public record BrandVisibleStatusUpdateRequest(
	@NotNull(message = "노출 상태는 필수입니다.")
	VisibleStatus visibleStatus
) {
}
