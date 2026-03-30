package com.loopers.ordering.order.application.port.out.client.coupon;


import java.math.BigDecimal;


/**
 * 쿠폰 적용 포트 (ordering BC → coupon BC)
 * - Cross-BC: ordering → coupon
 * - 구현체는 ACL 레이어에서 제공
 */
public interface OrderCouponApplier {

	// 쿠폰 적용 (issuedCouponId가 null이면 할인 없음)
	OrderCouponApplyResult apply(Long issuedCouponId, Long userId, BigDecimal totalPrice);

}
