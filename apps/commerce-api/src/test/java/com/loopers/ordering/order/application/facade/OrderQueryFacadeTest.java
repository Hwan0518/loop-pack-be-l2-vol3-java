package com.loopers.ordering.order.application.facade;


import com.loopers.ordering.order.application.dto.out.*;
import com.loopers.ordering.order.application.service.OrderCheckoutCommandService;
import com.loopers.ordering.order.application.service.OrderQueryService;
import com.loopers.ordering.order.domain.model.Order;
import com.loopers.ordering.order.domain.model.OrderItem;
import com.loopers.ordering.order.domain.model.vo.SnapshotName;
import com.loopers.ordering.order.domain.model.vo.SnapshotPrice;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.BDDMockito.given;


@ExtendWith(MockitoExtension.class)
@DisplayName("OrderQueryFacade 테스트")
class OrderQueryFacadeTest {

	@Mock
	private OrderCheckoutCommandService orderCheckoutCommandService;

	@Mock
	private OrderQueryService orderQueryService;

	private OrderQueryFacade orderQueryFacade;

	@BeforeEach
	void setUp() {
		orderQueryFacade = new OrderQueryFacade(orderCheckoutCommandService, orderQueryService);
	}


	private Order createTestOrder(Long id, Long userId) {
		return Order.reconstruct(id, userId, "req-test-" + id, new BigDecimal("200000"),
			BigDecimal.ZERO, new BigDecimal("200000"),
			List.of(OrderItem.reconstruct(1L, 1L,
				SnapshotName.from("나이키 에어맥스"),
				SnapshotPrice.from(new BigDecimal("100000")),
				2L)),
			null, LocalDateTime.now()
		);
	}


	@Nested
	@DisplayName("getOrder() 테스트")
	class GetOrderTest {

		@Test
		@DisplayName("[getOrder()] 유효한 loginId + password + orderId -> OrderDetailOutDto 반환. 인증 후 본인 주문 조회")
		void getOrderSuccess() {
			// Arrange
			String loginId = "loginId";
			String password = "password";
			Long userId = 100L;
			given(orderCheckoutCommandService.authenticate(loginId, password)).willReturn(userId);

			Order order = createTestOrder(1L, userId);
			given(orderQueryService.findByIdAndUserId(1L, userId)).willReturn(order);

			// Act
			OrderDetailOutDto result = orderQueryFacade.getOrder(loginId, password, 1L);

			// Assert
			assertAll(
				() -> assertThat(result.id()).isEqualTo(1L),
				() -> assertThat(result.userId()).isEqualTo(100L),
				() -> assertThat(result.items()).hasSize(1)
			);
		}

	}


	@Nested
	@DisplayName("getOrders() 테스트")
	class GetOrdersTest {

		@Test
		@DisplayName("[getOrders()] 날짜 필터 없이 사용자 주문 조회 -> OrderPageOutDto 반환. 인증 후 본인 주문 목록 조회")
		void getOrdersWithoutDateFilter() {
			// Arrange
			String loginId = "loginId";
			String password = "password";
			Long userId = 100L;
			given(orderCheckoutCommandService.authenticate(loginId, password)).willReturn(userId);

			OrderPageOutDto pageOutDto = new OrderPageOutDto(
				List.of(OrderOutDto.from(createTestOrder(1L, userId))),
				0, 20, 1
			);
			given(orderQueryService.getOrdersByUserId(userId, 0, 20, null, null)).willReturn(pageOutDto);

			// Act
			OrderPageOutDto result = orderQueryFacade.getOrders(loginId, password, 0, 20, null, null);

			// Assert
			assertAll(
				() -> assertThat(result.content()).hasSize(1),
				() -> assertThat(result.totalElements()).isEqualTo(1)
			);
		}


		@Test
		@DisplayName("[getOrders()] 날짜 필터 포함 사용자 주문 조회 -> OrderPageOutDto 반환. 인증 후 startDate~endDate 범위 전달")
		void getOrdersWithDateFilter() {
			// Arrange
			String loginId = "loginId";
			String password = "password";
			Long userId = 100L;
			given(orderCheckoutCommandService.authenticate(loginId, password)).willReturn(userId);

			LocalDate startDate = LocalDate.of(2026, 1, 1);
			LocalDate endDate = LocalDate.of(2026, 1, 31);
			OrderPageOutDto pageOutDto = new OrderPageOutDto(
				List.of(OrderOutDto.from(createTestOrder(1L, userId))),
				0, 20, 1
			);
			given(orderQueryService.getOrdersByUserId(userId, 0, 20, startDate, endDate)).willReturn(pageOutDto);

			// Act
			OrderPageOutDto result = orderQueryFacade.getOrders(loginId, password, 0, 20, startDate, endDate);

			// Assert
			assertAll(
				() -> assertThat(result.content()).hasSize(1),
				() -> assertThat(result.totalElements()).isEqualTo(1)
			);
		}

	}


	@Nested
	@DisplayName("getAdminOrder() 테스트")
	class GetAdminOrderTest {

		@Test
		@DisplayName("[getAdminOrder()] 주문 ID -> AdminOrderDetailOutDto 반환")
		void getAdminOrderSuccess() {
			// Arrange
			Order order = createTestOrder(1L, 100L);
			given(orderQueryService.findById(1L)).willReturn(order);

			// Act
			AdminOrderDetailOutDto result = orderQueryFacade.getAdminOrder(1L);

			// Assert
			assertAll(
				() -> assertThat(result.id()).isEqualTo(1L),
				() -> assertThat(result.userId()).isEqualTo(100L)
			);
		}

	}


	@Nested
	@DisplayName("getAdminOrders() 테스트")
	class GetAdminOrdersTest {

		@Test
		@DisplayName("[getAdminOrders()] 전체 주문 -> AdminOrderPageOutDto 반환")
		void getAdminOrdersSuccess() {
			// Arrange
			AdminOrderPageOutDto pageOutDto = new AdminOrderPageOutDto(
				List.of(AdminOrderOutDto.from(createTestOrder(1L, 100L))),
				0, 20, 1
			);
			given(orderQueryService.getAllOrders(0, 20)).willReturn(pageOutDto);

			// Act
			AdminOrderPageOutDto result = orderQueryFacade.getAdminOrders(0, 20);

			// Assert
			assertAll(
				() -> assertThat(result.content()).hasSize(1),
				() -> assertThat(result.totalElements()).isEqualTo(1)
			);
		}

	}

}
