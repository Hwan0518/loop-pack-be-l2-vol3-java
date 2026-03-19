package com.loopers.ordering.order.application.facade;


import com.loopers.ordering.order.application.dto.out.*;
import com.loopers.ordering.order.application.service.OrderQueryService;
import com.loopers.ordering.order.domain.model.Order;
import com.loopers.ordering.order.domain.model.OrderItem;
import com.loopers.ordering.order.domain.model.enums.OrderStatus;
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
	private OrderQueryService orderQueryService;

	private OrderQueryFacade orderQueryFacade;

	@BeforeEach
	void setUp() {
		orderQueryFacade = new OrderQueryFacade(orderQueryService);
	}


	private Order createTestOrder(Long id, Long userId) {
		return Order.reconstruct(id, userId, "req-test-" + id, new BigDecimal("200000"),
			BigDecimal.ZERO, new BigDecimal("200000"), OrderStatus.PENDING_PAYMENT,
			List.of(OrderItem.reconstruct(1L, 1L,
				SnapshotName.from("나이키 에어맥스"),
				SnapshotPrice.from(new BigDecimal("100000")),
				2L)),
			null, LocalDateTime.of(2026, 3, 10, 12, 0)
		);
	}


	@Nested
	@DisplayName("getOrder() 테스트")
	class GetOrderTest {

		@Test
		@DisplayName("[getOrder()] 유효한 userId + orderId -> OrderDetailOutDto 반환. "
			+ "DTO 변환 검증: 가격, 할인, 주문항목(상품명/가격/수량/소계), 쿠폰스냅샷, 생성일시")
		void getOrderSuccess() {
			// Arrange
			Long userId = 100L;
			Order order = createTestOrder(1L, userId);
			given(orderQueryService.findByIdAndUserId(1L, userId)).willReturn(order);

			// Act
			OrderDetailOutDto result = orderQueryFacade.getOrder(userId, 1L);

			// Assert — DTO 변환 결과 전체 필드 검증
			assertAll(
				() -> assertThat(result.id()).isEqualTo(1L),
				() -> assertThat(result.userId()).isEqualTo(userId),
				() -> assertThat(result.originalTotalPrice()).isEqualByComparingTo(new BigDecimal("200000")),
				() -> assertThat(result.discountAmount()).isEqualByComparingTo(BigDecimal.ZERO),
				() -> assertThat(result.totalPrice()).isEqualByComparingTo(new BigDecimal("200000")),
				() -> assertThat(result.couponSnapshot()).isNull(),
				() -> assertThat(result.createdAt()).isEqualTo(LocalDateTime.of(2026, 3, 10, 12, 0)),
				// 주문 항목 DTO 변환 검증
				() -> assertThat(result.items()).hasSize(1),
				() -> assertThat(result.items().get(0).productId()).isEqualTo(1L),
				() -> assertThat(result.items().get(0).snapshotName()).isEqualTo("나이키 에어맥스"),
				() -> assertThat(result.items().get(0).snapshotPrice()).isEqualByComparingTo(new BigDecimal("100000")),
				() -> assertThat(result.items().get(0).quantity()).isEqualTo(2L),
				// 소계 계산 검증 (snapshotPrice × quantity)
				() -> assertThat(result.items().get(0).subtotal()).isEqualByComparingTo(new BigDecimal("200000"))
			);
		}

	}


	@Nested
	@DisplayName("getOrders() 테스트")
	class GetOrdersTest {

		@Test
		@DisplayName("[getOrders()] 날짜 필터 없이 사용자 주문 조회 -> OrderPageOutDto 반환. 본인 주문 목록 조회")
		void getOrdersWithoutDateFilter() {
			// Arrange
			Long userId = 100L;

			OrderPageOutDto pageOutDto = new OrderPageOutDto(
				List.of(OrderOutDto.from(createTestOrder(1L, userId))),
				0, 20, 1
			);
			given(orderQueryService.getOrdersByUserId(userId, 0, 20, null, null)).willReturn(pageOutDto);

			// Act
			OrderPageOutDto result = orderQueryFacade.getOrders(userId, 0, 20, null, null);

			// Assert
			assertAll(
				() -> assertThat(result.content()).hasSize(1),
				() -> assertThat(result.totalElements()).isEqualTo(1),
				() -> assertThat(result.page()).isEqualTo(0),
				() -> assertThat(result.size()).isEqualTo(20)
			);
		}


		@Test
		@DisplayName("[getOrders()] 날짜 필터 포함 사용자 주문 조회 -> OrderPageOutDto 반환. startDate~endDate 범위 전달")
		void getOrdersWithDateFilter() {
			// Arrange
			Long userId = 100L;

			LocalDate startDate = LocalDate.of(2026, 1, 1);
			LocalDate endDate = LocalDate.of(2026, 1, 31);
			OrderPageOutDto pageOutDto = new OrderPageOutDto(
				List.of(OrderOutDto.from(createTestOrder(1L, userId))),
				0, 20, 1
			);
			given(orderQueryService.getOrdersByUserId(userId, 0, 20, startDate, endDate)).willReturn(pageOutDto);

			// Act
			OrderPageOutDto result = orderQueryFacade.getOrders(userId, 0, 20, startDate, endDate);

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
		@DisplayName("[getAdminOrder()] 주문 ID -> AdminOrderDetailOutDto 반환. "
			+ "DTO 변환 검증: totalPrice, 주문항목, 생성일시")
		void getAdminOrderSuccess() {
			// Arrange
			Order order = createTestOrder(1L, 100L);
			given(orderQueryService.findById(1L)).willReturn(order);

			// Act
			AdminOrderDetailOutDto result = orderQueryFacade.getAdminOrder(1L);

			// Assert — DTO 변환 결과 전체 필드 검증
			assertAll(
				() -> assertThat(result.id()).isEqualTo(1L),
				() -> assertThat(result.userId()).isEqualTo(100L),
				() -> assertThat(result.totalPrice()).isEqualByComparingTo(new BigDecimal("200000")),
				() -> assertThat(result.createdAt()).isEqualTo(LocalDateTime.of(2026, 3, 10, 12, 0)),
				// 주문 항목 DTO 변환 검증
				() -> assertThat(result.items()).hasSize(1),
				() -> assertThat(result.items().get(0).snapshotName()).isEqualTo("나이키 에어맥스"),
				() -> assertThat(result.items().get(0).snapshotPrice()).isEqualByComparingTo(new BigDecimal("100000")),
				() -> assertThat(result.items().get(0).quantity()).isEqualTo(2L)
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
				() -> assertThat(result.totalElements()).isEqualTo(1),
				() -> assertThat(result.page()).isEqualTo(0),
				() -> assertThat(result.size()).isEqualTo(20)
			);
		}

	}

}
