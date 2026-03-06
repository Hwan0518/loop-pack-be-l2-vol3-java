package com.loopers.coupon.coupontemplate.application.dto.in;


import java.time.LocalDateTime;


/**
 * 쿠폰 템플릿 수정 InDto (관리자)
 * - type, value, minOrderAmount는 생성 후 불변 (수정 대상 제외)
 */
public record AdminUpdateCouponTemplateInDto(
	String name,
	LocalDateTime expiredAt
) {
}
