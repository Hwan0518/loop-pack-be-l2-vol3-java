package com.loopers.ordering.order.interfaces.web.request;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;


/**
 * 주문 생성 요청
 * - cartItemIds: 주문할 장바구니 항목 ID 목록 (필수)
 * - requestId: 멱등성 보장용 요청 ID (필수)
 * - couponId: 적용할 발급 쿠폰 ID (선택)
 */
public record OrderCreateRequest(
	@NotEmpty(message = "장바구니 항목 ID 목록은 필수입니다.")
	List<@NotNull Long> cartItemIds,

	@NotBlank(message = "요청 ID는 필수입니다.")
	String requestId,

	Long couponId
) {
}
