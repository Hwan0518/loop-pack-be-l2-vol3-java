package com.loopers.coupon.issuedcoupon.application.dto.out;


import java.time.LocalDateTime;


/**
 * Kafka 발행용 쿠폰 발급 요청 Payload
 */
public record CouponIssueRequestedPayload(
	String requestId,
	Long userId,
	Long couponTemplateId,
	LocalDateTime occurredAt
) {

	// 팩토리 메서드
	public static CouponIssueRequestedPayload of(String requestId, Long userId, Long couponTemplateId) {
		return new CouponIssueRequestedPayload(requestId, userId, couponTemplateId, LocalDateTime.now());
	}

}
