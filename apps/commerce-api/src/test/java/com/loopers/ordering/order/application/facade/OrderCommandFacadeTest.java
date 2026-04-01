package com.loopers.ordering.order.application.facade;


import com.loopers.ordering.order.application.dto.in.OrderCreateInDto;
import com.loopers.ordering.order.application.dto.out.OrderDetailOutDto;
import com.loopers.ordering.order.application.port.out.client.cart.OrderCartItemInfo;
import com.loopers.ordering.order.application.port.out.client.catalog.OrderProductInfo;
import com.loopers.ordering.order.application.service.OrderCheckoutCommandService;
import com.loopers.ordering.order.application.service.OrderCommandService;
import com.loopers.ordering.order.application.service.OrderQueryService;
import com.loopers.ordering.order.domain.model.Order;
import com.loopers.ordering.order.domain.model.OrderItem;
import com.loopers.ordering.order.domain.model.enums.OrderStatus;
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
import org.springframework.dao.DataIntegrityViolationException;

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
	private OrderCheckoutCommandService orderCheckoutCommandService;

	@Mock
	private OrderQueryService orderQueryService;

	@Mock
	private OrderCommandService orderCommandService;

	private OrderCommandFacade orderCommandFacade;

	@BeforeEach
	void setUp() {
		orderCommandFacade = new OrderCommandFacade(
			orderCheckoutCommandService, orderQueryService, orderCommandService
		);
	}


	private Order createTestOrder(Long id, Long userId) {
		return Order.reconstruct(id, userId, "req-123", new BigDecimal("200000"),
			BigDecimal.ZERO, new BigDecimal("200000"), OrderStatus.PENDING_PAYMENT,
			List.of(OrderItem.reconstruct(1L, 1L,
				SnapshotName.from("나이키 에어맥스"),
				SnapshotPrice.from(new BigDecimal("100000")),
				2L)),
			null, LocalDateTime.now()
		);
	}


	@Nested
	@DisplayName("createOrder() 테스트")
	class CreateOrderTest {

		@Test
		@DisplayName("[createOrder()] 유효한 요청 + 기존 주문 없음 -> 주문 생성. 멱등성 확인 → 장바구니 조회 → 상품 조회 → 주문 생성")
		void createOrderSuccess() {
			// Arrange
			Long userId = 1L;
			List<Long> cartItemIds = List.of(100L);
			OrderCreateInDto inDto = new OrderCreateInDto(cartItemIds, "req-123", null);

			given(orderQueryService.findByUserIdAndRequestId(userId, "req-123"))
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
			OrderDetailOutDto expectedDto = OrderDetailOutDto.from(createTestOrder(10L, userId));
			given(orderCommandService.createOrder(eq(userId), eq(inDto), eq(cartItems), eq(products), eq(resolvedCartItemIds)))
				.willReturn(expectedDto);

			// Act
			OrderDetailOutDto result = orderCommandFacade.createOrder(userId, inDto, "entry-token");

			// Assert
			assertAll(
				() -> assertThat(result.id()).isEqualTo(10L),
				() -> assertThat(result.userId()).isEqualTo(1L),
				() -> verify(orderQueryService).findByUserIdAndRequestId(userId, "req-123"),
				() -> verify(orderCheckoutCommandService).validateAndLockEntryToken(userId, "entry-token"),
				() -> verify(orderCommandService).createOrder(eq(userId), eq(inDto), eq(cartItems), eq(products), eq(resolvedCartItemIds)),
				() -> verify(orderCheckoutCommandService).completeEntryToken(userId)
			);
		}


		@Test
		@DisplayName("[createOrder()] 동일 requestId로 재요청 -> 기존 주문 반환 (멱등성). createOrder 호출 없음")
		void createOrderIdempotent() {
			// Arrange
			Long userId = 1L;
			OrderCreateInDto inDto = new OrderCreateInDto(List.of(100L), "req-123", null);

			Order existingOrder = createTestOrder(10L, userId);
			given(orderQueryService.findByUserIdAndRequestId(userId, "req-123"))
				.willReturn(Optional.of(existingOrder));

			// Act
			OrderDetailOutDto result = orderCommandFacade.createOrder(userId, inDto, "entry-token");

			// Assert
			assertAll(
				() -> assertThat(result.id()).isEqualTo(10L),
				() -> verifyNoInteractions(orderCommandService)
			);
		}


		@Test
		@DisplayName("[createOrder()] 장바구니가 비어있음 -> EMPTY_CART 예외. 락 해제 + createOrder 미호출")
		void createOrderEmptyCart() {
			// Arrange
			Long userId = 1L;
			List<Long> cartItemIds = List.of(100L);
			OrderCreateInDto inDto = new OrderCreateInDto(cartItemIds, "req-123", null);

			given(orderQueryService.findByUserIdAndRequestId(userId, "req-123"))
				.willReturn(Optional.empty());

			willThrow(new CoreException(ErrorType.EMPTY_CART))
				.given(orderCheckoutCommandService).readCartItemsByIds(userId, cartItemIds);

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> orderCommandFacade.createOrder(userId, inDto, "entry-token"));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.EMPTY_CART),
				() -> verify(orderCheckoutCommandService).releaseOrderLock(userId),
				() -> verifyNoInteractions(orderCommandService)
			);
		}


		@Test
		@DisplayName("[createOrder()] DataIntegrityViolation 발생 (유니크 제약 race) -> 기존 주문 조회하여 반환")
		void createOrderRaceCondition() {
			// Arrange
			Long userId = 1L;
			List<Long> cartItemIds = List.of(100L);
			OrderCreateInDto inDto = new OrderCreateInDto(cartItemIds, "req-123", null);

			given(orderQueryService.findByUserIdAndRequestId(userId, "req-123"))
				.willReturn(Optional.empty())  // 첫 번째 호출: 멱등성 검사 시 없음
				.willReturn(Optional.of(createTestOrder(10L, userId))); // 두 번째 호출: race 후 조회

			List<OrderCartItemInfo> cartItems = List.of(
				new OrderCartItemInfo(100L, 1L, 2L)
			);
			given(orderCheckoutCommandService.readCartItemsByIds(userId, cartItemIds)).willReturn(cartItems);

			List<OrderProductInfo> products = List.of(
				new OrderProductInfo(1L, "나이키 에어맥스", new BigDecimal("100000"), 10L)
			);
			given(orderCheckoutCommandService.readProducts(List.of(1L))).willReturn(products);

			given(orderCommandService.createOrder(eq(userId), eq(inDto), any(), eq(products), any()))
				.willThrow(new DataIntegrityViolationException("Duplicate entry"));

			// Act
			OrderDetailOutDto result = orderCommandFacade.createOrder(userId, inDto, "entry-token");

			// Assert
			assertAll(
				() -> assertThat(result.id()).isEqualTo(10L),
				() -> verify(orderCommandService).createOrder(eq(userId), eq(inDto), any(), eq(products), any()),
				() -> verify(orderCheckoutCommandService).completeEntryToken(userId)
			);
		}

	}

}
