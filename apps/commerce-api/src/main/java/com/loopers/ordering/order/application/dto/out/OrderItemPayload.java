package com.loopers.ordering.order.application.dto.out;


/**
 * Kafka 발행용 주문 항목 Payload (OrderCreatedPayload, OrderPaidPayload 공용)
 */
public record OrderItemPayload(
	Long productId,
	Long quantity
) {
}
