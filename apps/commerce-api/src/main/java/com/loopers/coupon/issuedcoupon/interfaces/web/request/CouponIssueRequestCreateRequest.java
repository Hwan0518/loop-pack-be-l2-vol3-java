package com.loopers.coupon.issuedcoupon.interfaces.web.request;


import jakarta.validation.constraints.NotNull;


/**
 * 쿠폰 발급 요청 (사용자)
 */
public record CouponIssueRequestCreateRequest(
	@NotNull(message = "쿠폰 템플릿 ID는 필수입니다.")
	Long couponTemplateId,

	String requestId
) {
}
