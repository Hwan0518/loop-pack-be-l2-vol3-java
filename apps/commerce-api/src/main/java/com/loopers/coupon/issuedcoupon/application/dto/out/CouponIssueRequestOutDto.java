package com.loopers.coupon.issuedcoupon.application.dto.out;


/**
 * 쿠폰 발급 요청 결과 OutDto
 */
public record CouponIssueRequestOutDto(
	String requestId,
	String status
) {
}
