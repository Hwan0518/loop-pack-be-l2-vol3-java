package com.loopers.coupon.coupontemplate.domain.service.discount;


import java.math.BigDecimal;
import java.math.RoundingMode;


/**
 * 정률 할인 정책
 * - totalPrice * value / 100, 내림(floor) 처리 (원 단위 절사)
 */
public class RateDiscountPolicy implements DiscountPolicy {

	private final BigDecimal rate;

	public RateDiscountPolicy(BigDecimal rate) {
		this.rate = rate;
	}


	// 정률 할인: totalPrice * rate / 100, 소수점 내림
	@Override
	public BigDecimal calculate(BigDecimal totalPrice) {
		return totalPrice.multiply(rate)
			.divide(BigDecimal.valueOf(100), 0, RoundingMode.FLOOR);
	}

}
