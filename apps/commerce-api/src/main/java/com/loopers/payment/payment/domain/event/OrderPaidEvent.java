package com.loopers.payment.payment.domain.event;


import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;


/**
 * 주문 결제 완료 이벤트
 * @kafka ORDER_PAID → MetricsCollectorConsumer (sales_count), UserActionLogConsumer
 */
public record OrderPaidEvent(
	Long orderId,
	Long userId,
	Long paymentId,
	List<OrderPaidItemSnapshot> items,
	BigDecimal totalPrice,
	LocalDateTime occurredAt
) {

	public record OrderPaidItemSnapshot(Long productId, Long quantity) {}


	// 팩토리 메서드 — 결제 정보 + 주문 항목 스냅샷으로 생성
	public static OrderPaidEvent of(Long orderId, Long userId, Long paymentId,
		List<OrderPaidItemSnapshot> items, BigDecimal totalPrice) {
		return new OrderPaidEvent(orderId, userId, paymentId, items, totalPrice, LocalDateTime.now());
	}

}
