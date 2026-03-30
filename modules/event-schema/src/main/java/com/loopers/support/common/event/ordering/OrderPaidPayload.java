package com.loopers.support.common.event.ordering;


import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;


/**
 * Kafka 발행용 결제 완료 Payload
 */
public record OrderPaidPayload(
	Long orderId,
	Long userId,
	Long paymentId,
	List<OrderItemPayload> items,
	BigDecimal totalPrice,
	LocalDateTime occurredAt
) {

	// 팩토리 메서드
	public static OrderPaidPayload of(Long orderId, Long userId, Long paymentId,
		List<OrderItemPayload> items, BigDecimal totalPrice) {
		return new OrderPaidPayload(orderId, userId, paymentId, items, totalPrice, LocalDateTime.now());
	}

}
