package com.loopers.cart.cart.domain.model;


import com.loopers.cart.cart.domain.model.vo.Quantity;
import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;


@DisplayName("CartItem 도메인 모델 테스트")
class CartItemTest {

	@Nested
	@DisplayName("create()")
	class CreateTest {

		@Test
		@DisplayName("[create()] 유효한 값 -> CartItem 생성. id=null, selected=true")
		void createSuccess() {
			// Act
			CartItem cartItem = CartItem.create(1L, 100L, 2L);

			// Assert
			assertAll(
				() -> assertThat(cartItem.getId()).isNull(),
				() -> assertThat(cartItem.getUserId()).isEqualTo(1L),
				() -> assertThat(cartItem.getProductId()).isEqualTo(100L),
				() -> assertThat(cartItem.getQuantity().value()).isEqualTo(2L),
				() -> assertThat(cartItem.isSelected()).isTrue()
			);
		}


		@Test
		@DisplayName("[create()] userId null -> NullPointerException")
		void createWithNullUserId() {
			// Act & Assert
			assertThrows(NullPointerException.class,
				() -> CartItem.create(null, 100L, 2L));
		}


		@Test
		@DisplayName("[create()] productId null -> NullPointerException")
		void createWithNullProductId() {
			// Act & Assert
			assertThrows(NullPointerException.class,
				() -> CartItem.create(1L, null, 2L));
		}


		@Test
		@DisplayName("[create()] quantity null -> INVALID_QUANTITY 예외 (Quantity.create에서 검증)")
		void createWithNullQuantity() {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> CartItem.create(1L, 100L, null));

			// Assert
			assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_QUANTITY);
		}


		@Test
		@DisplayName("[create()] quantity 0 -> INVALID_QUANTITY 예외")
		void createWithZeroQuantity() {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> CartItem.create(1L, 100L, 0L));

			// Assert
			assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_QUANTITY);
		}

	}


	@Nested
	@DisplayName("reconstruct()")
	class ReconstructTest {

		@Test
		@DisplayName("[reconstruct()] DB 복원 -> 검증 없이 CartItem 생성. 모든 필드 포함")
		void reconstructSuccess() {
			// Arrange
			LocalDateTime now = LocalDateTime.now();
			Quantity quantity = Quantity.from(5L);

			// Act
			CartItem cartItem = CartItem.reconstruct(1L, 10L, 100L, quantity, false, now, now);

			// Assert
			assertAll(
				() -> assertThat(cartItem.getId()).isEqualTo(1L),
				() -> assertThat(cartItem.getUserId()).isEqualTo(10L),
				() -> assertThat(cartItem.getProductId()).isEqualTo(100L),
				() -> assertThat(cartItem.getQuantity().value()).isEqualTo(5L),
				() -> assertThat(cartItem.isSelected()).isFalse(),
				() -> assertThat(cartItem.getCreatedAt()).isEqualTo(now),
				() -> assertThat(cartItem.getUpdatedAt()).isEqualTo(now)
			);
		}

	}


	@Nested
	@DisplayName("addQuantity()")
	class AddQuantityTest {

		@Test
		@DisplayName("[addQuantity()] 양수 추가 -> 수량 합산")
		void addQuantitySuccess() {
			// Arrange
			CartItem cartItem = CartItem.create(1L, 100L, 3L);

			// Act
			cartItem.addQuantity(5L);

			// Assert
			assertThat(cartItem.getQuantity().value()).isEqualTo(8L);
		}


		@Test
		@DisplayName("[addQuantity()] 0 추가 -> INVALID_QUANTITY 예외")
		void addQuantityZero() {
			// Arrange
			CartItem cartItem = CartItem.create(1L, 100L, 3L);

			// Act & Assert
			CoreException exception = assertThrows(CoreException.class,
				() -> cartItem.addQuantity(0L));
			assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_QUANTITY);
		}

	}


	@Nested
	@DisplayName("changeQuantity()")
	class ChangeQuantityTest {

		@Test
		@DisplayName("[changeQuantity()] 유효한 수량 -> 수량 변경")
		void changeQuantitySuccess() {
			// Arrange
			CartItem cartItem = CartItem.create(1L, 100L, 3L);

			// Act
			cartItem.changeQuantity(10L);

			// Assert
			assertThat(cartItem.getQuantity().value()).isEqualTo(10L);
		}


		@Test
		@DisplayName("[changeQuantity()] 0 수량 -> INVALID_QUANTITY 예외")
		void changeQuantityZero() {
			// Arrange
			CartItem cartItem = CartItem.create(1L, 100L, 3L);

			// Act & Assert
			CoreException exception = assertThrows(CoreException.class,
				() -> cartItem.changeQuantity(0L));
			assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_QUANTITY);
		}

	}


	@Nested
	@DisplayName("select() / deselect()")
	class SelectionTest {

		@Test
		@DisplayName("[select()] 선택 해제 상태 -> 선택 상태로 변경")
		void selectSuccess() {
			// Arrange
			CartItem cartItem = CartItem.reconstruct(1L, 10L, 100L,
				Quantity.from(1L), false, LocalDateTime.now(), LocalDateTime.now());

			// Act
			cartItem.select();

			// Assert
			assertThat(cartItem.isSelected()).isTrue();
		}


		@Test
		@DisplayName("[deselect()] 선택 상태 -> 선택 해제 상태로 변경")
		void deselectSuccess() {
			// Arrange
			CartItem cartItem = CartItem.create(1L, 100L, 1L);

			// Act
			cartItem.deselect();

			// Assert
			assertThat(cartItem.isSelected()).isFalse();
		}

	}

}
