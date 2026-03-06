package com.loopers.coupon.issuedcoupon.interfaces.web.response;


import com.loopers.coupon.coupontemplate.domain.model.enums.CouponType;
import com.loopers.coupon.issuedcoupon.application.dto.out.IssuedCouponOutDto;
import com.loopers.coupon.issuedcoupon.domain.model.enums.CouponStatusView;

import java.math.BigDecimal;
import java.time.LocalDateTime;


/**
 * 발급된 쿠폰 응답 (사용자)
 */
public record IssuedCouponResponse(
	Long issuedCouponId,
	String templateName,
	CouponType type,
	BigDecimal value,
	BigDecimal minOrderAmount,
	LocalDateTime expiredAt,
	CouponStatusView status
) {

	// IssuedCouponOutDto를 응답 객체로 변환
	public static IssuedCouponResponse from(IssuedCouponOutDto outDto) {
		return new IssuedCouponResponse(
			outDto.issuedCouponId(),
			outDto.templateName(),
			outDto.type(),
			outDto.value(),
			outDto.minOrderAmount(),
			outDto.expiredAt(),
			outDto.status()
		);
	}

}
