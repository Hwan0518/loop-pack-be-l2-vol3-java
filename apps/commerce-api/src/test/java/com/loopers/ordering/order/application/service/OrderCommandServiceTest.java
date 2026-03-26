package com.loopers.ordering.order.application.service;


import com.loopers.coupon.issuedcoupon.application.dto.out.CouponApplyResult;
import com.loopers.ordering.order.application.dto.in.OrderCreateInDto;
import com.loopers.ordering.order.application.dto.out.OrderDetailOutDto;
import com.loopers.ordering.order.application.port.out.client.cart.OrderCartItemInfo;
import com.loopers.ordering.order.application.port.out.client.catalog.OrderProductInfo;
import com.loopers.ordering.order.application.port.out.client.catalog.OrderStockManager;
import com.loopers.ordering.order.application.port.out.client.coupon.OrderCouponApplier;
import com.loopers.ordering.order.application.port.out.client.coupon.OrderCouponRestorer;
import com.loopers.ordering.order.domain.event.OrderCreatedEvent;
import com.loopers.ordering.order.domain.model.Order;
import com.loopers.ordering.order.domain.repository.OrderCommandRepository;
import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.loopers.support.common.outbox.application.port.OutboxEventPort;
import com.loopers.support.common.outbox.application.util.JsonSerializer;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
@DisplayName("OrderCommandService 테스트")
class OrderCommandServiceTest {

	@Mock
	private OrderCommandRepository orderCommandRepository;

	@Mock
	private OrderStockManager orderStockManager;

	@Mock
	private OrderCouponApplier orderCouponApplier;

	@Mock
	private OrderCouponRestorer orderCouponRestorer;

	@Mock
	private OutboxEventPort outboxEventPort;

	@Mock
	private JsonSerializer jsonSerializer;

	@Mock
	private ApplicationEventPublisher eventPublisher;

	private OrderCommandService orderCommandService;

	@BeforeEach
	void setUp() {
		orderCommandService = new OrderCommandService(
			orderCommandRepository, orderStockManager, orderCouponApplier, orderCouponRestorer,
			outboxEventPort, jsonSerializer, eventPublisher
		);
	}


	@Nested
	@DisplayName("createOrder() 테스트")
	class ExecuteTest {

		@Test
		@DisplayName("[createOrder()] 유효한 요청 -> 재고 차감 + 주문 생성 + OrderCreatedEvent 발행. 전체 쓰기 플로우 성공")
		void executeSuccess() {
			// Arrange
			Long userId = 1L;
			OrderCreateInDto inDto = new OrderCreateInDto(List.of(100L), "req-123", null);
			List<OrderCartItemInfo> cartItems = List.of(
				new OrderCartItemInfo(100L, 1L, 2L),
				new OrderCartItemInfo(101L, 2L, 1L)
			);
			List<OrderProductInfo> products = List.of(
				new OrderProductInfo(1L, "나이키 에어맥스", new BigDecimal("100000"), 10L),
				new OrderProductInfo(2L, "아디다스 울트라부스트", new BigDecimal("200000"), 5L)
			);
			List<Long> resolvedCartItemIds = List.of(100L, 101L);

			given(orderCommandRepository.save(any(Order.class)))
				.willAnswer(invocation -> {
					Order order = invocation.getArgument(0);
					return Order.reconstruct(
						10L, order.getUserId(), order.getRequestId(), order.getOriginalTotalPrice(),
						order.getDiscountAmount(), order.getTotalPrice(), order.getStatus(),
						order.getItems(), order.getCouponSnapshot(), null
					);
				});

			// Act
			OrderDetailOutDto result = orderCommandService.createOrder(userId, inDto, cartItems, products, resolvedCartItemIds);

			// Assert
			ArgumentCaptor<OrderCreatedEvent> eventCaptor = ArgumentCaptor.forClass(OrderCreatedEvent.class);
			assertAll(
				() -> assertThat(result.id()).isEqualTo(10L),
				() -> assertThat(result.userId()).isEqualTo(1L),
				() -> assertThat(result.totalPrice()).isEqualByComparingTo(new BigDecimal("400000")),
				() -> assertThat(result.items()).hasSize(2),
				() -> verifyNoInteractions(orderCouponApplier),
				() -> verify(orderCommandRepository).save(any(Order.class)),
				() -> verify(eventPublisher).publishEvent(eventCaptor.capture())
			);

			OrderCreatedEvent event = eventCaptor.getValue();
			assertAll(
				() -> assertThat(event.orderId()).isEqualTo(10L),
				() -> assertThat(event.userId()).isEqualTo(1L),
				() -> assertThat(event.requestId()).isEqualTo("req-123"),
				() -> assertThat(event.cartItemIds()).containsExactly(100L, 101L),
				() -> assertThat(event.totalPrice()).isEqualByComparingTo(new BigDecimal("400000")),
				() -> assertThat(event.items()).hasSize(2),
				() -> assertThat(event.items().get(0).productId()).isEqualTo(1L),
				() -> assertThat(event.items().get(0).quantity()).isEqualTo(2L),
				() -> assertThat(event.occurredAt()).isNotNull()
			);
		}


		@Test
		@DisplayName("[createOrder()] 재고 차감 시 productId 오름차순 정렬 + 중복 수량 합산. 데드락 방지")
		void executeDecreaseStocksOrdered() {
			// Arrange
			Long userId = 1L;
			OrderCreateInDto inDto = new OrderCreateInDto(List.of(100L, 101L), "req-123", null);
			List<OrderCartItemInfo> cartItems = List.of(
				new OrderCartItemInfo(101L, 3L, 1L),
				new OrderCartItemInfo(100L, 1L, 2L),
				new OrderCartItemInfo(102L, 1L, 3L)
			);
			List<OrderProductInfo> products = List.of(
				new OrderProductInfo(1L, "상품A", new BigDecimal("10000"), 10L),
				new OrderProductInfo(3L, "상품B", new BigDecimal("20000"), 5L)
			);
			List<Long> resolvedCartItemIds = List.of(101L, 100L, 102L);

			given(orderCommandRepository.save(any(Order.class)))
				.willAnswer(invocation -> {
					Order order = invocation.getArgument(0);
					return Order.reconstruct(
						10L, order.getUserId(), order.getRequestId(), order.getOriginalTotalPrice(),
						order.getDiscountAmount(), order.getTotalPrice(), order.getStatus(),
						order.getItems(), order.getCouponSnapshot(), null
					);
				});

			// Act
			orderCommandService.createOrder(userId, inDto, cartItems, products, resolvedCartItemIds);

			// Assert — productId 오름차순(1L→3L), productId 1L은 수량 합산(2+3=5)
			InOrder inOrderVerify = inOrder(orderStockManager);
			inOrderVerify.verify(orderStockManager).decreaseStock(1L, 5L);
			inOrderVerify.verify(orderStockManager).decreaseStock(3L, 1L);
		}


		@Test
		@DisplayName("[createOrder()] 재고 부족 -> PRODUCT_OUT_OF_STOCK 예외. 재고 차감 실패 시 이후 단계 미실행")
		void executeOutOfStock() {
			// Arrange
			Long userId = 1L;
			OrderCreateInDto inDto = new OrderCreateInDto(List.of(100L), "req-123", null);
			List<OrderCartItemInfo> cartItems = List.of(
				new OrderCartItemInfo(100L, 1L, 100L)
			);
			List<OrderProductInfo> products = List.of(
				new OrderProductInfo(1L, "나이키 에어맥스", new BigDecimal("100000"), 5L)
			);
			List<Long> resolvedCartItemIds = List.of(100L);

			willThrow(new CoreException(ErrorType.PRODUCT_OUT_OF_STOCK))
				.given(orderStockManager).decreaseStock(1L, 100L);

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> orderCommandService.createOrder(userId, inDto, cartItems, products, resolvedCartItemIds));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.PRODUCT_OUT_OF_STOCK),
				() -> verifyNoInteractions(orderCommandRepository),
				() -> verifyNoInteractions(eventPublisher)
			);
		}


		@Test
		@DisplayName("[createOrder()] 쿠폰 적용 포함 주문 -> 할인 금액 반영된 주문 생성. 쿠폰 스냅샷 포함")
		void executeWithCoupon() {
			// Arrange
			Long userId = 1L;
			Long couponId = 5L;
			OrderCreateInDto inDto = new OrderCreateInDto(List.of(100L), "req-123", couponId);
			List<OrderCartItemInfo> cartItems = List.of(
				new OrderCartItemInfo(100L, 1L, 2L)
			);
			List<OrderProductInfo> products = List.of(
				new OrderProductInfo(1L, "나이키 에어맥스", new BigDecimal("100000"), 10L)
			);
			List<Long> resolvedCartItemIds = List.of(100L);

			CouponApplyResult couponResult = new CouponApplyResult(
				5L, new BigDecimal("10000"), "10% 할인 쿠폰", "PERCENTAGE", new BigDecimal("10"));
			given(orderCouponApplier.apply(couponId, userId, new BigDecimal("200000")))
				.willReturn(couponResult);

			given(orderCommandRepository.save(any(Order.class)))
				.willAnswer(invocation -> {
					Order order = invocation.getArgument(0);
					return Order.reconstruct(
						10L, order.getUserId(), order.getRequestId(), order.getOriginalTotalPrice(),
						order.getDiscountAmount(), order.getTotalPrice(), order.getStatus(),
						order.getItems(), order.getCouponSnapshot(), null
					);
				});

			// Act
			OrderDetailOutDto result = orderCommandService.createOrder(userId, inDto, cartItems, products, resolvedCartItemIds);

			// Assert
			assertAll(
				() -> assertThat(result.id()).isEqualTo(10L),
				() -> assertThat(result.originalTotalPrice()).isEqualByComparingTo(new BigDecimal("200000")),
				() -> assertThat(result.discountAmount()).isEqualByComparingTo(new BigDecimal("10000")),
				() -> assertThat(result.totalPrice()).isEqualByComparingTo(new BigDecimal("190000")),
				() -> assertThat(result.couponSnapshot()).isNotNull(),
				() -> assertThat(result.couponSnapshot().issuedCouponId()).isEqualTo(5L)
			);
		}

	}

}
