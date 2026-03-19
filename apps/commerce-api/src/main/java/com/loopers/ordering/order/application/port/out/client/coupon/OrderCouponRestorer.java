package com.loopers.ordering.order.application.port.out.client.coupon;


/**
 * 주문용 쿠폰 복원 포트 (보상 트랜잭션 — 주문 만료 시 USED → AVAILABLE)
 */
public interface OrderCouponRestorer {

	/**
	 * 쿠폰 복원
	 * 1. 사용된 쿠폰을 AVAILABLE 상태로 복원
	 */

	// 1. 쿠폰 복원
	void restoreCoupon(Long issuedCouponId);

}
