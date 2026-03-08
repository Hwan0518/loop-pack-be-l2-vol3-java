package com.loopers.coupon.coupontemplate.domain.model.enums;


import com.loopers.coupon.coupontemplate.domain.service.discount.DiscountPolicy;
import com.loopers.coupon.coupontemplate.domain.service.discount.FixedDiscountPolicy;
import com.loopers.coupon.coupontemplate.domain.service.discount.RateDiscountPolicy;

import java.math.BigDecimal;


/**
 * 쿠폰 타입
 * - FIXED: 정액 할인
 * - RATE: 정률 할인 (%)
 */
public enum CouponType {

	FIXED,
	RATE;


	/**
	 * 할인 정책 팩토리
	 * 1. 쿠폰 타입에 맞는 할인 정책 생성
	 */

	// 1. 쿠폰 타입에 맞는 DiscountPolicy 생성
	public DiscountPolicy createPolicy(BigDecimal value) {
		return switch (this) {
			case FIXED -> new FixedDiscountPolicy(value);
			case RATE -> new RateDiscountPolicy(value);
		};
	}

}
