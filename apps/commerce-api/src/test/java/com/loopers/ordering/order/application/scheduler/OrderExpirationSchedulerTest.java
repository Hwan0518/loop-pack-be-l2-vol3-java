package com.loopers.ordering.order.application.scheduler;


import com.loopers.ordering.order.application.port.out.client.payment.OrderPaymentReader;
import com.loopers.ordering.order.application.service.OrderCommandService;
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
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
@DisplayName("OrderExpirationScheduler 테스트")
class OrderExpirationSchedulerTest {

	@Mock
	private OrderQueryService orderQueryService;
	@Mock
	private OrderCommandService orderCommandService;
	@Mock
	private OrderPaymentReader orderPaymentReader;

	private OrderExpirationScheduler scheduler;

	@BeforeEach
	void setUp() {
		scheduler = new OrderExpirationScheduler(
			orderQueryService, orderCommandService, orderPaymentReader
		);
	}

	private Order createTestOrder(Long id, OrderStatus status) {
		return Order.reconstruct(id, 100L, "req-" + id, new BigDecimal("20000"),
			BigDecimal.ZERO, new BigDecimal("20000"), status,
			List.of(OrderItem.reconstruct(1L, 1L,
				SnapshotName.from("상품"), SnapshotPrice.from(new BigDecimal("10000")), 2L)),
			null, LocalDateTime.now().minusMinutes(31));
	}


	@Nested
	@DisplayName("expireOrders() 테스트")
	class ExpireOrdersTest {

		@Test
		@DisplayName("[expireOrders()] 만료 대상 주문 + REQUESTED 결제 없음 -> expireOrder 호출")
		void expireOrderSuccess() {
			// Arrange
			Order order = createTestOrder(1L, OrderStatus.PENDING_PAYMENT);
			given(orderQueryService.findExpirableOrders(any(LocalDateTime.class))).willReturn(List.of(order));
			given(orderPaymentReader.existsRequestedPayment(1L)).willReturn(false);

			// Act
			scheduler.expireOrders();

			// Assert
			verify(orderCommandService).expireOrder(order);
		}


		@Test
		@DisplayName("[expireOrders()] 만료 대상 주문 + REQUESTED 결제 존재 -> skip (expireOrder 미호출)")
		void expireOrderSkipWhenPaymentInProgress() {
			// Arrange
			Order order = createTestOrder(1L, OrderStatus.PENDING_PAYMENT);
			given(orderQueryService.findExpirableOrders(any(LocalDateTime.class))).willReturn(List.of(order));
			given(orderPaymentReader.existsRequestedPayment(1L)).willReturn(true);

			// Act
			scheduler.expireOrders();

			// Assert
			verify(orderCommandService, never()).expireOrder(any());
		}


		@Test
		@DisplayName("[expireOrders()] 만료 대상 없음 -> expireOrder 미호출")
		void expireOrderNoTarget() {
			// Arrange
			given(orderQueryService.findExpirableOrders(any(LocalDateTime.class))).willReturn(List.of());

			// Act
			scheduler.expireOrders();

			// Assert
			verify(orderCommandService, never()).expireOrder(any());
		}


		@Test
		@DisplayName("[expireOrders()] 1건 실패 + 1건 성공 -> 실패 건이 성공 건에 영향 X")
		void expireOrderPartialFailure() {
			// Arrange
			Order order1 = createTestOrder(1L, OrderStatus.PENDING_PAYMENT);
			Order order2 = createTestOrder(2L, OrderStatus.PAYMENT_FAILED);
			given(orderQueryService.findExpirableOrders(any(LocalDateTime.class))).willReturn(List.of(order1, order2));
			given(orderPaymentReader.existsRequestedPayment(1L)).willReturn(false);
			given(orderPaymentReader.existsRequestedPayment(2L)).willReturn(false);
			willThrow(new RuntimeException("재고 복원 실패")).given(orderCommandService).expireOrder(order1);

			// Act
			scheduler.expireOrders();

			// Assert — order2는 정상 처리
			verify(orderCommandService).expireOrder(order2);
		}

	}

}
