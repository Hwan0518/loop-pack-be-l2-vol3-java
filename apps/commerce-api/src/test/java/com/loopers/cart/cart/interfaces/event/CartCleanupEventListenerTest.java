package com.loopers.cart.cart.interfaces.event;


import com.loopers.cart.cart.application.service.CartItemCommandService;
import com.loopers.catalog.product.domain.event.ProductDeletedEvent;
import com.loopers.ordering.order.domain.event.OrderCreatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
@DisplayName("CartCleanupEventListener 단위 테스트")
class CartCleanupEventListenerTest {

	@Mock
	private CartItemCommandService cartItemCommandService;

	private CartCleanupEventListener cartCleanupEventListener;


	@BeforeEach
	void setUp() {
		cartCleanupEventListener = new CartCleanupEventListener(cartItemCommandService);
	}


	@Test
	@DisplayName("[handleProductDeleted()] 상품 삭제 이벤트 수신 -> 해당 상품 ID로 장바구니 항목 전체 삭제")
	void handleProductDeleted() {
		// Arrange
		Long productId = 100L;
		ProductDeletedEvent event = new ProductDeletedEvent(productId);
		willDoNothing().given(cartItemCommandService).deleteAllByProductId(productId);

		// Act
		cartCleanupEventListener.handleProductDeleted(event);

		// Assert
		verify(cartItemCommandService).deleteAllByProductId(productId);
	}


	@Test
	@DisplayName("[handleOrderCreated()] 주문 생성 이벤트 수신 -> 사용자 ID와 장바구니 항목 ID 목록으로 삭제 (userId 스코프 적용)")
	void handleOrderCreated() {
		// Arrange
		Long orderId = 1L;
		Long userId = 10L;
		List<Long> cartItemIds = List.of(1L, 2L, 3L);
		OrderCreatedEvent event = new OrderCreatedEvent(orderId, userId, cartItemIds);
		willDoNothing().given(cartItemCommandService).deleteAllByUserIdAndIds(userId, cartItemIds);

		// Act
		cartCleanupEventListener.handleOrderCreated(event);

		// Assert
		verify(cartItemCommandService).deleteAllByUserIdAndIds(userId, cartItemIds);
	}

}
