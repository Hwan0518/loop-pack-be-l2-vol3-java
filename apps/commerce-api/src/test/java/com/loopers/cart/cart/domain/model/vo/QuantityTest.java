package com.loopers.cart.cart.domain.model.vo;


import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;


@DisplayName("Quantity 값 객체 테스트")
class QuantityTest {

	@Nested
	@DisplayName("create()")
	class CreateTest {

		@Test
		@DisplayName("[create()] 양수 값 -> Quantity 생성 성공")
		void createWithPositiveValue() {
			// Arrange
			Long value = 5L;

			// Act
			Quantity quantity = Quantity.create(value);

			// Assert
			assertThat(quantity.value()).isEqualTo(5L);
		}


		@Test
		@DisplayName("[create()] null 값 -> INVALID_QUANTITY 예외")
		void createWithNull() {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> Quantity.create(null));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_QUANTITY),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_QUANTITY.getMessage())
			);
		}


		@Test
		@DisplayName("[create()] 0 값 -> INVALID_QUANTITY 예외")
		void createWithZero() {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> Quantity.create(0L));

			// Assert
			assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_QUANTITY);
		}


		@Test
		@DisplayName("[create()] 음수 값 -> INVALID_QUANTITY 예외")
		void createWithNegative() {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> Quantity.create(-1L));

			// Assert
			assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_QUANTITY);
		}

	}


	@Nested
	@DisplayName("from()")
	class FromTest {

		@Test
		@DisplayName("[from()] DB 복원용 생성 -> 검증 없이 Quantity 생성")
		void fromValue() {
			// Act
			Quantity quantity = Quantity.from(10L);

			// Assert
			assertThat(quantity.value()).isEqualTo(10L);
		}

	}


	@Nested
	@DisplayName("add()")
	class AddTest {

		@Test
		@DisplayName("[add()] 양수 추가 -> 합산된 새 Quantity 반환")
		void addPositiveAmount() {
			// Arrange
			Quantity quantity = Quantity.create(3L);

			// Act
			Quantity result = quantity.add(5L);

			// Assert
			assertAll(
				() -> assertThat(result.value()).isEqualTo(8L),
				() -> assertThat(quantity.value()).isEqualTo(3L)
			);
		}


		@Test
		@DisplayName("[add()] null 추가 -> INVALID_QUANTITY 예외")
		void addNull() {
			// Arrange
			Quantity quantity = Quantity.create(3L);

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> quantity.add(null));

			// Assert
			assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_QUANTITY);
		}


		@Test
		@DisplayName("[add()] 0 추가 -> INVALID_QUANTITY 예외")
		void addZero() {
			// Arrange
			Quantity quantity = Quantity.create(3L);

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> quantity.add(0L));

			// Assert
			assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_QUANTITY);
		}

	}

}
