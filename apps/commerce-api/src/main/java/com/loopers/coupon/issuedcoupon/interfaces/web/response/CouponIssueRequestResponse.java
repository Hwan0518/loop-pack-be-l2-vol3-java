package com.loopers.coupon.issuedcoupon.interfaces.web.response;


import com.loopers.coupon.issuedcoupon.application.dto.out.CouponIssueRequestOutDto;


/**
 * 쿠폰 발급 요청 응답
 */
public record CouponIssueRequestResponse(
	String requestId,
	String status
) {

	public static CouponIssueRequestResponse from(CouponIssueRequestOutDto outDto) {
		return new CouponIssueRequestResponse(outDto.requestId(), outDto.status());
	}

}
