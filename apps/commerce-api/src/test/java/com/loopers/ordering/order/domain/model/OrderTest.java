package com.loopers.ordering.order.domain.model;


import com.loopers.ordering.order.domain.model.vo.SnapshotName;
import com.loopers.ordering.order.domain.model.vo.SnapshotPrice;
import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;


@DisplayName("Order 도메인 모델 테스트")
class OrderTest {

	@Nested
	@DisplayName("create() 테스트")
	class CreateTest {

		@Test
		@DisplayName("[Order.create()] 유효한 인자 -> Order 생성. id=null, createdAt=null")
		void createSuccess() {
			// Arrange
			List<OrderItem> items = List.of(
				OrderItem.create(1L, "나이키 에어맥스", new BigDecimal("100000"), 2L)
			);

			// Act
			Order order = Order.create(1L, new BigDecimal("200000"), items);

			// Assert
			assertAll(
				() -> assertThat(order.getId()).isNull(),
				() -> assertThat(order.getUserId()).isEqualTo(1L),
				() -> assertThat(order.getTotalPrice()).isEqualByComparingTo(new BigDecimal("200000")),
				() -> assertThat(order.getItems()).hasSize(1),
				() -> assertThat(order.getCreatedAt()).isNull()
			);
		}


		@Test
		@DisplayName("[Order.create()] userId가 null -> NullPointerException 예외")
		void createFailWhenUserIdIsNull() {
			// Arrange
			List<OrderItem> items = List.of(
				OrderItem.create(1L, "나이키 에어맥스", new BigDecimal("100000"), 2L)
			);

			// Act & Assert
			assertThrows(NullPointerException.class,
				() -> Order.create(null, new BigDecimal("200000"), items));
		}


		@Test
		@DisplayName("[Order.create()] totalPrice가 null -> INVALID_ORDER_TOTAL_PRICE 예외")
		void createFailWhenTotalPriceIsNull() {
			// Arrange
			List<OrderItem> items = List.of(
				OrderItem.create(1L, "나이키 에어맥스", new BigDecimal("100000"), 2L)
			);

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> Order.create(1L, null, items));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_ORDER_TOTAL_PRICE),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_ORDER_TOTAL_PRICE.getMessage())
			);
		}


		@Test
		@DisplayName("[Order.create()] totalPrice가 음수 -> INVALID_ORDER_TOTAL_PRICE 예외")
		void createFailWhenTotalPriceIsNegative() {
			// Arrange
			List<OrderItem> items = List.of(
				OrderItem.create(1L, "나이키 에어맥스", new BigDecimal("100000"), 2L)
			);

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> Order.create(1L, new BigDecimal("-1"), items));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_ORDER_TOTAL_PRICE),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_ORDER_TOTAL_PRICE.getMessage())
			);
		}


		@Test
		@DisplayName("[Order.create()] items가 null -> ORDER_EMPTY_ITEMS 예외")
		void createFailWhenItemsIsNull() {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> Order.create(1L, new BigDecimal("200000"), null));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.ORDER_EMPTY_ITEMS),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.ORDER_EMPTY_ITEMS.getMessage())
			);
		}


		@Test
		@DisplayName("[Order.create()] items가 빈 리스트 -> ORDER_EMPTY_ITEMS 예외")
		void createFailWhenItemsIsEmpty() {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> Order.create(1L, new BigDecimal("200000"), Collections.emptyList()));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.ORDER_EMPTY_ITEMS),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.ORDER_EMPTY_ITEMS.getMessage())
			);
		}

	}


	@Nested
	@DisplayName("reconstruct() 테스트")
	class ReconstructTest {

		@Test
		@DisplayName("[Order.reconstruct()] 유효한 인자 -> Order 복원. 검증 없이 값 설정")
		void reconstructSuccess() {
			// Arrange
			LocalDateTime createdAt = LocalDateTime.now();
			List<OrderItem> items = List.of(
				OrderItem.reconstruct(10L, 1L,
					SnapshotName.from("나이키 에어맥스"),
					SnapshotPrice.from(new BigDecimal("100000")),
					2L)
			);

			// Act
			Order order = Order.reconstruct(1L, 100L, new BigDecimal("200000"), items, createdAt);

			// Assert
			assertAll(
				() -> assertThat(order.getId()).isEqualTo(1L),
				() -> assertThat(order.getUserId()).isEqualTo(100L),
				() -> assertThat(order.getTotalPrice()).isEqualByComparingTo(new BigDecimal("200000")),
				() -> assertThat(order.getItems()).hasSize(1),
				() -> assertThat(order.getCreatedAt()).isEqualTo(createdAt)
			);
		}

	}

}
