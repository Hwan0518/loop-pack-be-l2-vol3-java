package com.loopers.coupon.coupontemplate.interfaces.web.response;


import com.loopers.coupon.coupontemplate.application.dto.out.AdminCouponTemplateOutDto;
import com.loopers.coupon.coupontemplate.domain.model.enums.CouponType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;


/**
 * 쿠폰 템플릿 응답 (관리자)
 */
public record AdminCouponTemplateResponse(
	Long id,
	String name,
	CouponType type,
	BigDecimal value,
	BigDecimal minOrderAmount,
	LocalDateTime expiredAt,
	ZonedDateTime deletedAt
) {

	// AdminCouponTemplateOutDto를 응답 객체로 변환
	public static AdminCouponTemplateResponse from(AdminCouponTemplateOutDto outDto) {
		return new AdminCouponTemplateResponse(
			outDto.id(),
			outDto.name(),
			outDto.type(),
			outDto.value(),
			outDto.minOrderAmount(),
			outDto.expiredAt(),
			outDto.deletedAt()
		);
	}

}
