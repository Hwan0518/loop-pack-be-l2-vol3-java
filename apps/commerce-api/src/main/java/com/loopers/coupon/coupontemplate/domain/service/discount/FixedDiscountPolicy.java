package com.loopers.coupon.coupontemplate.domain.service.discount;


import java.math.BigDecimal;


/**
 * 정액 할인 정책
 * - 고정 할인액(value)을 그대로 반환
 */
public class FixedDiscountPolicy implements DiscountPolicy {

	private final BigDecimal value;

	public FixedDiscountPolicy(BigDecimal value) {
		this.value = value;
	}


	// 정액 할인: 고정 할인액 반환
	@Override
	public BigDecimal calculate(BigDecimal totalPrice) {
		return value;
	}

}
