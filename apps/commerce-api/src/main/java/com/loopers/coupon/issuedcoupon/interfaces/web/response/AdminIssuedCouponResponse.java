package com.loopers.coupon.issuedcoupon.interfaces.web.response;


import com.loopers.coupon.issuedcoupon.application.dto.out.AdminIssuedCouponOutDto;
import com.loopers.coupon.issuedcoupon.domain.model.enums.CouponStatusView;

import java.time.ZonedDateTime;


/**
 * 발급된 쿠폰 응답 (관리자)
 */
public record AdminIssuedCouponResponse(
	Long id,
	Long couponTemplateId,
	Long userId,
	CouponStatusView status,
	ZonedDateTime createdAt
) {

	// AdminIssuedCouponOutDto를 응답 객체로 변환
	public static AdminIssuedCouponResponse from(AdminIssuedCouponOutDto outDto) {
		return new AdminIssuedCouponResponse(
			outDto.id(),
			outDto.couponTemplateId(),
			outDto.userId(),
			outDto.status(),
			outDto.createdAt()
		);
	}

}
