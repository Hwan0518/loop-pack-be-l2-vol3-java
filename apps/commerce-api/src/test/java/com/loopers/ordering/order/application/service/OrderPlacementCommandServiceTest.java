package com.loopers.ordering.order.application.service;


import com.loopers.ordering.order.application.port.out.client.cart.OrderCartItemInfo;
import com.loopers.ordering.order.application.port.out.client.catalog.OrderProductInfo;
import com.loopers.ordering.order.domain.model.Order;
import com.loopers.ordering.order.domain.repository.IdempotencyKeyCommandRepository;
import com.loopers.ordering.order.domain.repository.OrderCommandRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
@DisplayName("OrderPlacementCommandService 테스트")
class OrderPlacementCommandServiceTest {

	@Mock
	private OrderCommandRepository orderCommandRepository;

	@Mock
	private IdempotencyKeyCommandRepository idempotencyKeyCommandRepository;

	private OrderPlacementCommandService orderPlacementCommandService;

	@BeforeEach
	void setUp() {
		orderPlacementCommandService = new OrderPlacementCommandService(
			orderCommandRepository, idempotencyKeyCommandRepository
		);
	}


	@Nested
	@DisplayName("createOrder() 테스트")
	class CreateOrderTest {

		@Test
		@DisplayName("[createOrder()] 유효한 장바구니 항목 + 상품 정보 -> 주문 생성 및 저장. 멱등성 키도 저장")
		void createOrderSuccess() {
			// Arrange
			Long userId = 1L;
			String requestId = "req-123";
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
						10L, order.getUserId(), order.getTotalPrice(),
						order.getItems(), null
					);
				});

			given(idempotencyKeyCommandRepository.save(any()))
				.willAnswer(invocation -> invocation.getArgument(0));

			// Act
			Order result = orderPlacementCommandService.createOrder(userId, requestId, cartItems, products, resolvedCartItemIds);

			// Assert
			assertAll(
				() -> assertThat(result.getId()).isEqualTo(10L),
				() -> assertThat(result.getUserId()).isEqualTo(1L),
				() -> assertThat(result.getTotalPrice()).isEqualByComparingTo(new BigDecimal("400000")),
				() -> assertThat(result.getItems()).hasSize(2),
				() -> verify(orderCommandRepository).save(any(Order.class)),
				() -> verify(idempotencyKeyCommandRepository).save(any())
			);
		}

	}

}
