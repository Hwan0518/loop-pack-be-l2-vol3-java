package com.loopers.coupon.coupontemplate.application.dto.in;


import com.loopers.coupon.coupontemplate.domain.model.enums.CouponType;

import java.math.BigDecimal;
import java.time.LocalDateTime;


/**
 * 쿠폰 템플릿 생성 InDto (관리자)
 */
public record AdminCreateCouponTemplateInDto(
	String name,
	CouponType type,
	BigDecimal value,
	BigDecimal minOrderAmount,
	Integer maxQuantity,
	LocalDateTime expiredAt
) {
}
