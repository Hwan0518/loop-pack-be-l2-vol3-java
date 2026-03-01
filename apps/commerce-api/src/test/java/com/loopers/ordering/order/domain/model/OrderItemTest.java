package com.loopers.ordering.order.domain.model;


import com.loopers.ordering.order.domain.model.vo.SnapshotName;
import com.loopers.ordering.order.domain.model.vo.SnapshotPrice;
import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;


@DisplayName("OrderItem 도메인 모델 테스트")
class OrderItemTest {

	@Nested
	@DisplayName("create() 테스트")
	class CreateTest {

		@Test
		@DisplayName("[OrderItem.create()] 유효한 인자 -> OrderItem 생성. id=null")
		void createSuccess() {
			// Act
			OrderItem orderItem = OrderItem.create(1L, "나이키 에어맥스", new BigDecimal("100000"), 2L);

			// Assert
			assertAll(
				() -> assertThat(orderItem.getId()).isNull(),
				() -> assertThat(orderItem.getProductId()).isEqualTo(1L),
				() -> assertThat(orderItem.getSnapshotName().value()).isEqualTo("나이키 에어맥스"),
				() -> assertThat(orderItem.getSnapshotPrice().value()).isEqualByComparingTo(new BigDecimal("100000")),
				() -> assertThat(orderItem.getQuantity()).isEqualTo(2L)
			);
		}


		@Test
		@DisplayName("[OrderItem.create()] productId가 null -> NullPointerException 예외")
		void createFailWhenProductIdIsNull() {
			// Act & Assert
			assertThrows(NullPointerException.class,
				() -> OrderItem.create(null, "나이키 에어맥스", new BigDecimal("100000"), 2L));
		}


		@Test
		@DisplayName("[OrderItem.create()] quantity가 null -> INVALID_ORDER_QUANTITY 예외")
		void createFailWhenQuantityIsNull() {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> OrderItem.create(1L, "나이키 에어맥스", new BigDecimal("100000"), null));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_ORDER_QUANTITY),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_ORDER_QUANTITY.getMessage())
			);
		}


		@Test
		@DisplayName("[OrderItem.create()] quantity가 0 이하 -> INVALID_ORDER_QUANTITY 예외")
		void createFailWhenQuantityIsZeroOrLess() {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> OrderItem.create(1L, "나이키 에어맥스", new BigDecimal("100000"), 0L));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_ORDER_QUANTITY),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_ORDER_QUANTITY.getMessage())
			);
		}

	}


	@Nested
	@DisplayName("reconstruct() 테스트")
	class ReconstructTest {

		@Test
		@DisplayName("[OrderItem.reconstruct()] 유효한 인자 -> OrderItem 복원. 검증 없이 값 설정")
		void reconstructSuccess() {
			// Act
			OrderItem orderItem = OrderItem.reconstruct(
				10L, 1L,
				SnapshotName.from("나이키 에어맥스"),
				SnapshotPrice.from(new BigDecimal("100000")),
				2L
			);

			// Assert
			assertAll(
				() -> assertThat(orderItem.getId()).isEqualTo(10L),
				() -> assertThat(orderItem.getProductId()).isEqualTo(1L),
				() -> assertThat(orderItem.getSnapshotName().value()).isEqualTo("나이키 에어맥스"),
				() -> assertThat(orderItem.getSnapshotPrice().value()).isEqualByComparingTo(new BigDecimal("100000")),
				() -> assertThat(orderItem.getQuantity()).isEqualTo(2L)
			);
		}

	}


	@Nested
	@DisplayName("getSubtotal() 테스트")
	class GetSubtotalTest {

		@Test
		@DisplayName("[OrderItem.getSubtotal()] 가격 100000 * 수량 2 -> 200000 반환")
		void getSubtotalSuccess() {
			// Arrange
			OrderItem orderItem = OrderItem.create(1L, "나이키 에어맥스", new BigDecimal("100000"), 2L);

			// Act
			BigDecimal subtotal = orderItem.getSubtotal();

			// Assert
			assertThat(subtotal).isEqualByComparingTo(new BigDecimal("200000"));
		}


		@Test
		@DisplayName("[OrderItem.getSubtotal()] 가격 0 * 수량 5 -> 0 반환")
		void getSubtotalWhenPriceIsZero() {
			// Arrange
			OrderItem orderItem = OrderItem.create(1L, "무료 상품", BigDecimal.ZERO, 5L);

			// Act
			BigDecimal subtotal = orderItem.getSubtotal();

			// Assert
			assertThat(subtotal).isEqualByComparingTo(BigDecimal.ZERO);
		}

	}

}
