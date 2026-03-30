package com.loopers.coupon.application.dto;


/**
 * 쿠폰 템플릿 조회 결과 DTO (application 계층)
 * - maxQuantity: 최대 발급 수량 (null = 무제한)
 * - deleted: 소프트 삭제 여부
 * - expired: 만료 여부
 */
public record CouponTemplateInfo(
	Long id,
	Integer maxQuantity,
	boolean deleted,
	boolean expired
) {
}
