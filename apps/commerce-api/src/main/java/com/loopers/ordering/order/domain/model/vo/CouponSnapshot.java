package com.loopers.ordering.order.domain.model.vo;


import java.math.BigDecimal;


/**
 * 주문 시 적용된 쿠폰 스냅샷 (불변 Value Object)
 * - issuedCouponId: 발급 쿠폰 ID
 * - name: 쿠폰 이름 스냅샷
 * - type: 쿠폰 타입 스냅샷 (String — BC 결합 방지)
 * - value: 할인 값 스냅샷
 */
public record CouponSnapshot(
	Long issuedCouponId,
	String name,
	String type,
	BigDecimal value
) {
}
