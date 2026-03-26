package com.loopers.support.common.event;


import com.loopers.catalog.product.domain.event.ProductViewedEvent;
import com.loopers.engagement.productlike.domain.event.ProductLikedEvent;
import com.loopers.ordering.order.domain.event.OrderCreatedEvent;
import com.loopers.payment.payment.domain.event.OrderPaidEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;


/**
 * 유저 행동 로깅 이벤트 리스너 (Step 2 이후 제거 — Kafka 중앙 로그로 대체)
 * 1. ORDER 로깅
 * 2. PAYMENT 로깅
 * 3. LIKE 로깅
 * 4. VIEW 로깅
 */

@Component
@Slf4j
public class UserActionEventListener {

	// 1. ORDER 로깅
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	@Async
	public void handleOrderCreated(OrderCreatedEvent event) {
		log.info("[UserAction] userId={} action=ORDER target=ORDER:{}", event.userId(), event.orderId());
	}


	// 2. PAYMENT 로깅
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	@Async
	public void handleOrderPaid(OrderPaidEvent event) {
		log.info("[UserAction] userId={} action=PAYMENT target=PAYMENT:{}", event.userId(), event.paymentId());
	}


	// 3. LIKE 로깅
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	@Async
	public void handleProductLiked(ProductLikedEvent event) {
		log.info("[UserAction] userId={} action=LIKE target=PRODUCT:{}", event.userId(), event.productId());
	}


	// 4. VIEW 로깅
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	@Async
	public void handleProductViewed(ProductViewedEvent event) {
		log.info("[UserAction] userId={} action=VIEW target=PRODUCT:{}", event.userId(), event.productId());
	}

}
