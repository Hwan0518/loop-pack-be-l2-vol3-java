package com.loopers.coupon.issuedcoupon.domain.model.enums;


/**
 * 발급된 쿠폰 상태 (DB 저장용)
 * - AVAILABLE: 사용 가능
 * - USED: 사용됨
 * (EXPIRED는 동적 계산 — template.expiredAt < now OR template.deletedAt != null)
 */
public enum IssuedCouponStatus {
	AVAILABLE,
	USED
}
