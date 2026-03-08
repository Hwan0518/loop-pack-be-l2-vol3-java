package com.loopers.coupon.coupontemplate.domain.service.discount;


import java.math.BigDecimal;


/**
 * 할인 정책 인터페이스 (전략 패턴)
 * - FIXED: FixedDiscountPolicy
 * - RATE: RateDiscountPolicy
 */
public interface DiscountPolicy {

	// 할인 금액 계산 (내림 처리, 원 단위 절사)
	BigDecimal calculate(BigDecimal totalPrice);

}
