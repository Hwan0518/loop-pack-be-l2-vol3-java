package com.loopers.ordering.order.interfaces.event;


import com.loopers.ordering.order.application.port.out.client.cart.OrderCartItemCleaner;
import com.loopers.ordering.order.domain.event.OrderCreatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
@DisplayName("OrderEventListener 테스트")
class OrderEventListenerTest {

	@Mock
	private OrderCartItemCleaner orderCartItemCleaner;

	private OrderEventListener orderEventListener;


	@BeforeEach
	void setUp() {
		orderEventListener = new OrderEventListener(orderCartItemCleaner);
	}


	@Test
	@DisplayName("[handleCartCleanup()] OrderCreatedEvent 수신 -> 장바구니 삭제 위임")
	void handleCartCleanupSuccess() {
		// Arrange
		List<Long> cartItemIds = List.of(100L, 101L);
		OrderCreatedEvent event = new OrderCreatedEvent(
			1L, 10L, "req-123", cartItemIds,
			List.of(new OrderCreatedEvent.OrderItemSnapshot(1L, 2L)),
			new BigDecimal("200000"), LocalDateTime.now()
		);

		// Act
		orderEventListener.handleCartCleanup(event);

		// Assert
		verify(orderCartItemCleaner).deleteCartItems(10L, cartItemIds);
	}


	@Test
	@DisplayName("[handleCartCleanup()] 장바구니 삭제 실패 -> 예외 무시 (로깅만)")
	void handleCartCleanupFailure() {
		// Arrange
		List<Long> cartItemIds = List.of(100L);
		OrderCreatedEvent event = new OrderCreatedEvent(
			1L, 10L, "req-123", cartItemIds,
			List.of(new OrderCreatedEvent.OrderItemSnapshot(1L, 2L)),
			new BigDecimal("200000"), LocalDateTime.now()
		);
		willThrow(new RuntimeException("삭제 실패")).given(orderCartItemCleaner).deleteCartItems(10L, cartItemIds);

		// Act — 예외가 전파되지 않음
		orderEventListener.handleCartCleanup(event);

		// Assert
		verify(orderCartItemCleaner).deleteCartItems(10L, cartItemIds);
	}

}
