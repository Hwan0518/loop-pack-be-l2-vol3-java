package com.loopers.ordering.order.application.service;


import com.loopers.ordering.order.application.dto.out.AdminOrderPageOutDto;
import com.loopers.ordering.order.application.dto.out.OrderPageOutDto;
import com.loopers.ordering.order.domain.model.Order;
import com.loopers.ordering.order.domain.model.OrderItem;
import com.loopers.ordering.order.domain.model.vo.SnapshotName;
import com.loopers.ordering.order.domain.model.vo.SnapshotPrice;
import com.loopers.ordering.order.domain.repository.OrderQueryRepository;
import com.loopers.ordering.order.domain.repository.vo.PageCriteria;
import com.loopers.ordering.order.domain.repository.vo.PageResult;
import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;


@ExtendWith(MockitoExtension.class)
@DisplayName("OrderQueryService 테스트")
class OrderQueryServiceTest {

	@Mock
	private OrderQueryRepository orderQueryRepository;

	private OrderQueryService orderQueryService;

	@BeforeEach
	void setUp() {
		orderQueryService = new OrderQueryService(orderQueryRepository);
	}


	private Order createTestOrder(Long id, Long userId) {
		return Order.reconstruct(id, userId, new BigDecimal("200000"),
			List.of(OrderItem.reconstruct(1L, 1L,
				SnapshotName.from("나이키 에어맥스"),
				SnapshotPrice.from(new BigDecimal("100000")),
				2L)),
			LocalDateTime.now()
		);
	}


	@Nested
	@DisplayName("findById() 테스트")
	class FindByIdTest {

		@Test
		@DisplayName("[findById()] 존재하는 주문 ID -> Order 반환")
		void findByIdSuccess() {
			// Arrange
			Order order = createTestOrder(1L, 100L);
			given(orderQueryRepository.findById(1L)).willReturn(Optional.of(order));

			// Act
			Order result = orderQueryService.findById(1L);

			// Assert
			assertThat(result.getId()).isEqualTo(1L);
		}


		@Test
		@DisplayName("[findById()] 존재하지 않는 주문 ID -> ORDER_NOT_FOUND 예외")
		void findByIdNotFound() {
			// Arrange
			given(orderQueryRepository.findById(999L)).willReturn(Optional.empty());

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> orderQueryService.findById(999L));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.ORDER_NOT_FOUND),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.ORDER_NOT_FOUND.getMessage())
			);
		}

	}


	@Nested
	@DisplayName("findByIdAndUserId() 테스트")
	class FindByIdAndUserIdTest {

		@Test
		@DisplayName("[findByIdAndUserId()] 존재하는 주문 + 본인 주문 -> Order 반환")
		void findByIdAndUserIdSuccess() {
			// Arrange
			Order order = createTestOrder(1L, 100L);
			given(orderQueryRepository.findById(1L)).willReturn(Optional.of(order));

			// Act
			Order result = orderQueryService.findByIdAndUserId(1L, 100L);

			// Assert
			assertAll(
				() -> assertThat(result.getId()).isEqualTo(1L),
				() -> assertThat(result.getUserId()).isEqualTo(100L)
			);
		}


		@Test
		@DisplayName("[findByIdAndUserId()] 존재하는 주문 + 다른 사용자 -> ORDER_NOT_FOUND 예외")
		void findByIdAndUserIdNotOwner() {
			// Arrange
			Order order = createTestOrder(1L, 100L);
			given(orderQueryRepository.findById(1L)).willReturn(Optional.of(order));

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> orderQueryService.findByIdAndUserId(1L, 999L));

			// Assert
			assertThat(exception.getErrorType()).isEqualTo(ErrorType.ORDER_NOT_FOUND);
		}


		@Test
		@DisplayName("[findByIdAndUserId()] 존재하지 않는 주문 ID -> ORDER_NOT_FOUND 예외")
		void findByIdAndUserIdOrderNotFound() {
			// Arrange
			given(orderQueryRepository.findById(999L)).willReturn(Optional.empty());

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> orderQueryService.findByIdAndUserId(999L, 100L));

			// Assert
			assertThat(exception.getErrorType()).isEqualTo(ErrorType.ORDER_NOT_FOUND);
		}

	}


	@Nested
	@DisplayName("getOrdersByUserId() 테스트")
	class GetOrdersByUserIdTest {

		@Test
		@DisplayName("[getOrdersByUserId()] 날짜 필터 없이 주문 조회 -> OrderPageOutDto 반환. startDate/endDate null일 때 전체 조회")
		void getOrdersByUserIdWithoutDateFilter() {
			// Arrange
			Order order = createTestOrder(1L, 100L);
			PageResult<Order> pageResult = new PageResult<>(List.of(order), 0, 20, 1);
			given(orderQueryRepository.findByUserId(100L, null, null, new PageCriteria(0, 20)))
				.willReturn(pageResult);

			// Act
			OrderPageOutDto result = orderQueryService.getOrdersByUserId(100L, 0, 20, null, null);

			// Assert
			assertAll(
				() -> assertThat(result.content()).hasSize(1),
				() -> assertThat(result.totalElements()).isEqualTo(1)
			);
		}


		@Test
		@DisplayName("[getOrdersByUserId()] 날짜 필터 포함 주문 조회 -> OrderPageOutDto 반환. startDate~endDate 범위 조회")
		void getOrdersByUserIdWithDateFilter() {
			// Arrange
			LocalDate startDate = LocalDate.of(2026, 1, 1);
			LocalDate endDate = LocalDate.of(2026, 1, 31);
			Order order = createTestOrder(1L, 100L);
			PageResult<Order> pageResult = new PageResult<>(List.of(order), 0, 20, 1);
			given(orderQueryRepository.findByUserId(100L, startDate, endDate, new PageCriteria(0, 20)))
				.willReturn(pageResult);

			// Act
			OrderPageOutDto result = orderQueryService.getOrdersByUserId(100L, 0, 20, startDate, endDate);

			// Assert
			assertAll(
				() -> assertThat(result.content()).hasSize(1),
				() -> assertThat(result.totalElements()).isEqualTo(1)
			);
		}

	}


	@Nested
	@DisplayName("getAllOrders() 테스트")
	class GetAllOrdersTest {

		@Test
		@DisplayName("[getAllOrders()] 주문 존재 -> AdminOrderPageOutDto 반환")
		void getAllOrdersSuccess() {
			// Arrange
			Order order = createTestOrder(1L, 100L);
			PageResult<Order> pageResult = new PageResult<>(List.of(order), 0, 20, 1);
			given(orderQueryRepository.findAll(new PageCriteria(0, 20)))
				.willReturn(pageResult);

			// Act
			AdminOrderPageOutDto result = orderQueryService.getAllOrders(0, 20);

			// Assert
			assertAll(
				() -> assertThat(result.content()).hasSize(1),
				() -> assertThat(result.totalElements()).isEqualTo(1)
			);
		}

	}

}
