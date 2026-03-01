package com.loopers.ordering.order.application.facade;


import com.loopers.ordering.order.application.dto.in.OrderCreateInDto;
import com.loopers.ordering.order.application.dto.out.OrderDetailOutDto;
import com.loopers.ordering.order.application.port.out.client.cart.OrderCartItemInfo;
import com.loopers.ordering.order.application.port.out.client.catalog.OrderProductInfo;
import com.loopers.ordering.order.application.service.OrderCheckoutCommandService;
import com.loopers.ordering.order.application.service.OrderIdempotencyQueryService;
import com.loopers.ordering.order.application.service.OrderPlacementCommandService;
import com.loopers.ordering.order.application.service.OrderQueryService;
import com.loopers.ordering.order.domain.model.Order;
import com.loopers.ordering.order.domain.model.OrderItem;
import com.loopers.ordering.order.domain.model.vo.SnapshotName;
import com.loopers.ordering.order.domain.model.vo.SnapshotPrice;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
@DisplayName("OrderCommandFacade 테스트")
class OrderCommandFacadeTest {

	@Mock
	private OrderIdempotencyQueryService orderIdempotencyQueryService;

	@Mock
	private OrderCheckoutCommandService orderCheckoutCommandService;

	@Mock
	private OrderPlacementCommandService orderPlacementCommandService;

	@Mock
	private OrderQueryService orderQueryService;

	private OrderCommandFacade orderCommandFacade;

	@BeforeEach
	void setUp() {
		orderCommandFacade = new OrderCommandFacade(
			orderIdempotencyQueryService, orderCheckoutCommandService,
			orderPlacementCommandService, orderQueryService
		);
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
	@DisplayName("createOrder() 테스트")
	class CreateOrderTest {

		@Test
		@DisplayName("[createOrder()] 유효한 요청 + 멱등성 키 없음 -> 주문 생성 + 장바구니 정리. 인증 → 장바구니 조회 → 상품 조회 → 재고 차감 → 주문 생성 → 장바구니 정리")
		void createOrderSuccess() {
			// Arrange
			String loginId = "loginId";
			String password = "password";
			Long userId = 1L;
			List<Long> cartItemIds = List.of(100L);
			OrderCreateInDto inDto = new OrderCreateInDto(cartItemIds, "req-123");

			given(orderCheckoutCommandService.authenticate(loginId, password)).willReturn(userId);

			given(orderIdempotencyQueryService.findOrderIdByRequestId(userId, "req-123"))
				.willReturn(Optional.empty());

			List<OrderCartItemInfo> cartItems = List.of(
				new OrderCartItemInfo(100L, 1L, 2L)
			);
			given(orderCheckoutCommandService.readCartItemsByIds(userId, cartItemIds)).willReturn(cartItems);

			List<OrderProductInfo> products = List.of(
				new OrderProductInfo(1L, "나이키 에어맥스", new BigDecimal("100000"), 10L)
			);
			given(orderCheckoutCommandService.readProducts(List.of(1L))).willReturn(products);

			List<Long> resolvedCartItemIds = List.of(100L);
			Order savedOrder = createTestOrder(10L, userId);
			given(orderPlacementCommandService.createOrder(
				eq(userId), eq("req-123"), eq(cartItems), eq(products), eq(resolvedCartItemIds)
			)).willReturn(savedOrder);

			willDoNothing().given(orderPlacementCommandService).deleteCartItems(userId, resolvedCartItemIds);

			// Act
			OrderDetailOutDto result = orderCommandFacade.createOrder(loginId, password, inDto);

			// Assert
			assertAll(
				() -> assertThat(result.id()).isEqualTo(10L),
				() -> assertThat(result.userId()).isEqualTo(1L),
				() -> verify(orderCheckoutCommandService).authenticate(loginId, password),
				() -> verify(orderCheckoutCommandService).decreaseStocks(cartItems),
				() -> verify(orderPlacementCommandService).createOrder(
					eq(userId), eq("req-123"), eq(cartItems), eq(products), eq(resolvedCartItemIds)),
				() -> verify(orderPlacementCommandService).deleteCartItems(userId, resolvedCartItemIds)
			);
		}


		@Test
		@DisplayName("[createOrder()] 동일 requestId로 재요청 -> 기존 주문 반환 (멱등성). 주문 생성 호출 없음")
		void createOrderIdempotent() {
			// Arrange
			String loginId = "loginId";
			String password = "password";
			Long userId = 1L;
			OrderCreateInDto inDto = new OrderCreateInDto(List.of(100L), "req-123");

			given(orderCheckoutCommandService.authenticate(loginId, password)).willReturn(userId);

			given(orderIdempotencyQueryService.findOrderIdByRequestId(userId, "req-123"))
				.willReturn(Optional.of(10L));

			Order existingOrder = createTestOrder(10L, userId);
			given(orderQueryService.findById(10L)).willReturn(existingOrder);

			// Act
			OrderDetailOutDto result = orderCommandFacade.createOrder(loginId, password, inDto);

			// Assert
			assertAll(
				() -> assertThat(result.id()).isEqualTo(10L),
				() -> verifyNoMoreInteractions(orderPlacementCommandService)
			);
		}


		@Test
		@DisplayName("[createOrder()] 장바구니가 비어있음 -> EMPTY_CART 예외. 재고 차감/주문 생성 미호출")
		void createOrderEmptyCart() {
			// Arrange
			String loginId = "loginId";
			String password = "password";
			Long userId = 1L;
			List<Long> cartItemIds = List.of(100L);
			OrderCreateInDto inDto = new OrderCreateInDto(cartItemIds, "req-123");

			given(orderCheckoutCommandService.authenticate(loginId, password)).willReturn(userId);

			given(orderIdempotencyQueryService.findOrderIdByRequestId(userId, "req-123"))
				.willReturn(Optional.empty());

			willThrow(new CoreException(ErrorType.EMPTY_CART))
				.given(orderCheckoutCommandService).readCartItemsByIds(userId, cartItemIds);

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> orderCommandFacade.createOrder(loginId, password, inDto));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.EMPTY_CART),
				() -> verifyNoMoreInteractions(orderPlacementCommandService)
			);
		}

	}

}
