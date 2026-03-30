package com.loopers.ordering.order.interfaces.event;


import com.loopers.ordering.order.application.port.out.client.cart.OrderCartItemCleaner;
import com.loopers.ordering.order.domain.event.OrderCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;


/**
 * 주문 이벤트 리스너
 * 1. 장바구니 정리 (주문 커밋 후 비동기)
 */

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventListener {

	// port
	private final OrderCartItemCleaner orderCartItemCleaner;


	// 1. 장바구니 정리 (주문 커밋 후 비동기)
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	@Async
	public void handleCartCleanup(OrderCreatedEvent event) {
		try {
			orderCartItemCleaner.deleteCartItems(event.userId(), event.cartItemIds());
		} catch (Exception e) {
			log.warn("[OrderEvent] 장바구니 정리 실패 orderId={}", event.orderId(), e);
		}
	}

}
