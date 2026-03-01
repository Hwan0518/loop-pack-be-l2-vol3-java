package com.loopers.ordering.order.application.service;


import com.loopers.ordering.order.application.port.out.client.cart.OrderCartItemCleaner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
@DisplayName("OrderCleanupCommandService 테스트")
class OrderCleanupCommandServiceTest {

	@Mock
	private OrderCartItemCleaner orderCartItemCleaner;

	private OrderCleanupCommandService orderCleanupCommandService;


	@BeforeEach
	void setUp() {
		orderCleanupCommandService = new OrderCleanupCommandService(orderCartItemCleaner);
	}


	@Nested
	@DisplayName("deleteCartItems() - 장바구니 항목 삭제")
	class DeleteCartItemsTest {

		@Test
		@DisplayName("[deleteCartItems()] 유효한 사용자 ID + 장바구니 ID 목록 -> OrderCartItemCleaner에 위임")
		void deleteCartItemsSuccess() {
			// Arrange
			Long userId = 1L;
			List<Long> cartItemIds = List.of(100L, 101L);
			willDoNothing().given(orderCartItemCleaner).deleteCartItems(userId, cartItemIds);

			// Act
			orderCleanupCommandService.deleteCartItems(userId, cartItemIds);

			// Assert
			verify(orderCartItemCleaner).deleteCartItems(userId, cartItemIds);
		}

	}

}
