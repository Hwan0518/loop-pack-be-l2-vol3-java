package com.loopers.ordering.order.application.dto.out;


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
}
