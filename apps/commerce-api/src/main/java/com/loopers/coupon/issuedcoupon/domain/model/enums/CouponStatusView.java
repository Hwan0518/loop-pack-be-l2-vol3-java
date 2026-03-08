package com.loopers.coupon.issuedcoupon.domain.model.enums;


/**
 * 쿠폰 상태 뷰 (API 응답용, 동적 계산)
 * - AVAILABLE: 사용 가능
 * - USED: 사용됨
 * - EXPIRED: 만료됨 (template.expiredAt < now OR template.deletedAt != null)
 */
public enum CouponStatusView {
	AVAILABLE,
	USED,
	EXPIRED
}
