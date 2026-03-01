package com.loopers.catalog.product.domain.model.vo;


import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;


@DisplayName("Money 값 객체 테스트")
class MoneyTest {

	@Nested
	@DisplayName("create()")
	class CreateTest {

		@Test
		@DisplayName("[create()] 양수 금액 -> Money 생성 성공")
		void createWithPositiveValue() {
			// Act
			Money money = Money.create(new BigDecimal("10000"));

			// Assert
			assertThat(money.value()).isEqualByComparingTo(new BigDecimal("10000"));
		}


		@Test
		@DisplayName("[create()] 0원 -> Money 생성 성공 (최소 허용)")
		void createWithZero() {
			// Act
			Money money = Money.create(BigDecimal.ZERO);

			// Assert
			assertThat(money.value()).isEqualByComparingTo(BigDecimal.ZERO);
		}


		@Test
		@DisplayName("[create()] null -> INVALID_PRODUCT_PRICE 예외")
		void createWithNull() {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> Money.create(null));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_PRODUCT_PRICE),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_PRODUCT_PRICE.getMessage())
			);
		}


		@Test
		@DisplayName("[create()] 음수 금액 -> INVALID_PRODUCT_PRICE 예외")
		void createWithNegative() {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> Money.create(new BigDecimal("-1")));

			// Assert
			assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_PRODUCT_PRICE);
		}

	}


	@Nested
	@DisplayName("from()")
	class FromTest {

		@Test
		@DisplayName("[from()] 유효한 값 -> Money 생성 (검증 생략)")
		void fromValidValue() {
			// Act
			Money money = Money.from(new BigDecimal("5000"));

			// Assert
			assertThat(money.value()).isEqualByComparingTo(new BigDecimal("5000"));
		}


		@Test
		@DisplayName("[from()] null -> NullPointerException")
		void fromNull() {
			// Act & Assert
			assertThrows(NullPointerException.class,
				() -> Money.from(null));
		}

	}

}
