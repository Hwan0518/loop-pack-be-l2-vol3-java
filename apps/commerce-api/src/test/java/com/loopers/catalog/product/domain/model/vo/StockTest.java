package com.loopers.catalog.product.domain.model.vo;


import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;


@DisplayName("Stock 값 객체 테스트")
class StockTest {

	@Nested
	@DisplayName("create()")
	class CreateTest {

		@Test
		@DisplayName("[create()] 양수 재고 -> Stock 생성 성공")
		void createWithPositiveValue() {
			// Act
			Stock stock = Stock.create(100L);

			// Assert
			assertThat(stock.value()).isEqualTo(100L);
		}


		@Test
		@DisplayName("[create()] 0 재고 -> Stock 생성 성공 (최소 허용)")
		void createWithZero() {
			// Act
			Stock stock = Stock.create(0L);

			// Assert
			assertThat(stock.value()).isEqualTo(0L);
		}


		@Test
		@DisplayName("[create()] null -> INVALID_PRODUCT_STOCK 예외")
		void createWithNull() {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> Stock.create(null));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_PRODUCT_STOCK),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_PRODUCT_STOCK.getMessage())
			);
		}


		@Test
		@DisplayName("[create()] 음수 재고 -> INVALID_PRODUCT_STOCK 예외")
		void createWithNegative() {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> Stock.create(-1L));

			// Assert
			assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_PRODUCT_STOCK);
		}

	}


	@Nested
	@DisplayName("from()")
	class FromTest {

		@Test
		@DisplayName("[from()] 유효한 값 -> Stock 생성 (검증 생략)")
		void fromValidValue() {
			// Act
			Stock stock = Stock.from(50L);

			// Assert
			assertThat(stock.value()).isEqualTo(50L);
		}


		@Test
		@DisplayName("[from()] null -> NullPointerException")
		void fromNull() {
			// Act & Assert
			assertThrows(NullPointerException.class,
				() -> Stock.from(null));
		}

	}


	@Nested
	@DisplayName("increase()")
	class IncreaseTest {

		@Test
		@DisplayName("[increase()] 유효한 증가 -> 증가된 Stock 반환")
		void increaseValid() {
			// Arrange
			Stock stock = Stock.create(100L);

			// Act
			Stock increased = stock.increase(30L);

			// Assert
			assertThat(increased.value()).isEqualTo(130L);
		}


		@Test
		@DisplayName("[increase()] null 증가 수량 -> INVALID_PRODUCT_STOCK 예외")
		void increaseNull() {
			// Arrange
			Stock stock = Stock.create(10L);

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> stock.increase(null));

			// Assert
			assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_PRODUCT_STOCK);
		}


		@Test
		@DisplayName("[increase()] 0 증가 수량 -> INVALID_PRODUCT_STOCK 예외")
		void increaseZero() {
			// Arrange
			Stock stock = Stock.create(10L);

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> stock.increase(0L));

			// Assert
			assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_PRODUCT_STOCK);
		}


		@Test
		@DisplayName("[increase()] 음수 증가 수량 -> INVALID_PRODUCT_STOCK 예외")
		void increaseNegative() {
			// Arrange
			Stock stock = Stock.create(10L);

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> stock.increase(-5L));

			// Assert
			assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_PRODUCT_STOCK);
		}

	}


	@Nested
	@DisplayName("decrease()")
	class DecreaseTest {

		@Test
		@DisplayName("[decrease()] 유효한 차감 -> 차감된 Stock 반환")
		void decreaseValid() {
			// Arrange
			Stock stock = Stock.create(100L);

			// Act
			Stock decreased = stock.decrease(30L);

			// Assert
			assertThat(decreased.value()).isEqualTo(70L);
		}


		@Test
		@DisplayName("[decrease()] 전체 차감 -> 0 Stock 반환")
		void decreaseAll() {
			// Arrange
			Stock stock = Stock.create(50L);

			// Act
			Stock decreased = stock.decrease(50L);

			// Assert
			assertThat(decreased.value()).isEqualTo(0L);
		}


		@Test
		@DisplayName("[decrease()] 재고 초과 차감 -> PRODUCT_OUT_OF_STOCK 예외")
		void decreaseExceedsStock() {
			// Arrange
			Stock stock = Stock.create(10L);

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> stock.decrease(11L));

			// Assert
			assertThat(exception.getErrorType()).isEqualTo(ErrorType.PRODUCT_OUT_OF_STOCK);
		}


		@Test
		@DisplayName("[decrease()] null 차감 수량 -> INVALID_PRODUCT_STOCK 예외")
		void decreaseNull() {
			// Arrange
			Stock stock = Stock.create(10L);

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> stock.decrease(null));

			// Assert
			assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_PRODUCT_STOCK);
		}


		@Test
		@DisplayName("[decrease()] 0 차감 수량 -> INVALID_PRODUCT_STOCK 예외")
		void decreaseZero() {
			// Arrange
			Stock stock = Stock.create(10L);

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> stock.decrease(0L));

			// Assert
			assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_PRODUCT_STOCK);
		}


		@Test
		@DisplayName("[decrease()] 음수 차감 수량 -> INVALID_PRODUCT_STOCK 예외")
		void decreaseNegative() {
			// Arrange
			Stock stock = Stock.create(10L);

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> stock.decrease(-5L));

			// Assert
			assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_PRODUCT_STOCK);
		}

	}

}
