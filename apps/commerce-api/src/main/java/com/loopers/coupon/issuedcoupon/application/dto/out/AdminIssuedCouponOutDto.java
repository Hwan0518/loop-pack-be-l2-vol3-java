package com.loopers.coupon.issuedcoupon.application.dto.out;


import com.loopers.coupon.coupontemplate.domain.model.CouponTemplate;
import com.loopers.coupon.issuedcoupon.domain.model.IssuedCoupon;
import com.loopers.coupon.issuedcoupon.domain.model.enums.CouponStatusView;

import java.time.ZonedDateTime;


/**
 * 발급된 쿠폰 응답 OutDto (관리자)
 */
public record AdminIssuedCouponOutDto(
	Long id,
	Long couponTemplateId,
	Long userId,
	CouponStatusView status,
	ZonedDateTime createdAt
) {

	// IssuedCoupon + CouponTemplate 도메인 객체를 OutDto로 변환
	public static AdminIssuedCouponOutDto from(IssuedCoupon issuedCoupon, CouponTemplate template) {
		return new AdminIssuedCouponOutDto(
			issuedCoupon.getId(),
			issuedCoupon.getCouponTemplateId(),
			issuedCoupon.getUserId(),
			issuedCoupon.getStatusView(template),
			issuedCoupon.getCreatedAt()
		);
	}

}
