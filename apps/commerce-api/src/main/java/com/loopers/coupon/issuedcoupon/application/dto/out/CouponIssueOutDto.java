package com.loopers.coupon.issuedcoupon.application.dto.out;


import com.loopers.coupon.issuedcoupon.domain.model.IssuedCoupon;


/**
 * 쿠폰 발급 결과 DTO
 * - issuedCouponId: 발급된 쿠폰 ID
 */
public record CouponIssueOutDto(Long issuedCouponId) {

	// 1. IssuedCoupon 도메인 객체를 발급 결과 DTO로 변환
	public static CouponIssueOutDto from(IssuedCoupon issuedCoupon) {
		return new CouponIssueOutDto(issuedCoupon.getId());
	}

}
