package com.loopers.coupon.issuedcoupon.interfaces.web.response;


import com.loopers.coupon.issuedcoupon.application.dto.out.CouponIssueOutDto;


/**
 * 쿠폰 발급 응답
 * - issuedCouponId: 발급된 쿠폰 ID
 */
public record IssuedCouponIssueResponse(Long issuedCouponId) {

	// 1. CouponIssueOutDto를 응답 객체로 변환
	public static IssuedCouponIssueResponse from(CouponIssueOutDto outDto) {
		return new IssuedCouponIssueResponse(outDto.issuedCouponId());
	}

}
