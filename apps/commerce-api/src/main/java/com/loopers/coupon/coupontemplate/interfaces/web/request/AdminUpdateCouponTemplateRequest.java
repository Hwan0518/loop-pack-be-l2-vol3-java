package com.loopers.coupon.coupontemplate.interfaces.web.request;


import java.time.LocalDateTime;


/**
 * 쿠폰 템플릿 수정 요청 (관리자)
 * - type, value, minOrderAmount는 생성 후 불변 (수정 대상 제외)
 */
public record AdminUpdateCouponTemplateRequest(
	String name,
	LocalDateTime expiredAt
) {
}
