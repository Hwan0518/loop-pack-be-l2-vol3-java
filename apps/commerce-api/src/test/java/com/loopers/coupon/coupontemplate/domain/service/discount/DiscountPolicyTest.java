package com.loopers.coupon.coupontemplate.domain.service.discount;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;


@DisplayName("DiscountPolicy 단위 테스트")
class DiscountPolicyTest {

	@Nested
	@DisplayName("FixedDiscountPolicy")
	class FixedDiscountPolicyTest {

		@Test
		@DisplayName("[calculate()] 정액 할인 -> 주문 금액에 무관하게 고정 할인액 반환")
		void calculateFixed() {
			// Arrange
			DiscountPolicy policy = new FixedDiscountPolicy(new BigDecimal("5000"));

			// Act
			BigDecimal result = policy.calculate(new BigDecimal("30000"));

			// Assert
			assertThat(result).isEqualByComparingTo(new BigDecimal("5000"));
		}


		@Test
		@DisplayName("[calculate()] 주문 금액보다 큰 정액 할인 -> 할인액 그대로 반환 (초과 허용)")
		void calculateFixedExceedsTotalPrice() {
			// Arrange
			DiscountPolicy policy = new FixedDiscountPolicy(new BigDecimal("10000"));

			// Act
			BigDecimal result = policy.calculate(new BigDecimal("5000"));

			// Assert
			assertThat(result).isEqualByComparingTo(new BigDecimal("10000"));
		}

	}


	@Nested
	@DisplayName("RateDiscountPolicy")
	class RateDiscountPolicyTest {

		@Test
		@DisplayName("[calculate()] 10% 정률 할인, 30000원 -> 3000원")
		void calculateRate10Percent() {
			// Arrange
			DiscountPolicy policy = new RateDiscountPolicy(new BigDecimal("10"));

			// Act
			BigDecimal result = policy.calculate(new BigDecimal("30000"));

			// Assert
			assertThat(result).isEqualByComparingTo(new BigDecimal("3000"));
		}


		@Test
		@DisplayName("[calculate()] 소수점 발생 시 내림(floor) 처리 -> 원 단위 절사")
		void calculateRateFloor() {
			// Arrange
			// 10% of 10001 = 1000.1 → floor → 1000
			DiscountPolicy policy = new RateDiscountPolicy(new BigDecimal("10"));

			// Act
			BigDecimal result = policy.calculate(new BigDecimal("10001"));

			// Assert
			assertThat(result).isEqualByComparingTo(new BigDecimal("1000"));
		}


		@Test
		@DisplayName("[calculate()] 100% 정률 할인 -> 전액 할인")
		void calculateRate100Percent() {
			// Arrange
			DiscountPolicy policy = new RateDiscountPolicy(new BigDecimal("100"));

			// Act
			BigDecimal result = policy.calculate(new BigDecimal("20000"));

			// Assert
			assertThat(result).isEqualByComparingTo(new BigDecimal("20000"));
		}


		@Test
		@DisplayName("[calculate()] 1% 정률 할인, 100원 -> 1원")
		void calculateRate1Percent() {
			// Arrange
			DiscountPolicy policy = new RateDiscountPolicy(new BigDecimal("1"));

			// Act
			BigDecimal result = policy.calculate(new BigDecimal("100"));

			// Assert
			assertThat(result).isEqualByComparingTo(new BigDecimal("1"));
		}


		@Test
		@DisplayName("[calculate()] 소수점 비율 할인 -> 내림 처리")
		void calculateRateDecimalFloor() {
			// Arrange
			// 15% of 999 = 149.85 → floor → 149
			DiscountPolicy policy = new RateDiscountPolicy(new BigDecimal("15"));

			// Act
			BigDecimal result = policy.calculate(new BigDecimal("999"));

			// Assert
			assertThat(result).isEqualByComparingTo(new BigDecimal("149"));
		}

	}

}
