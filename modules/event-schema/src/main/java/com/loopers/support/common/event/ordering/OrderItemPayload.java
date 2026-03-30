package com.loopers.support.common.event.ordering;


/**
 * Kafka 발행용 주문 항목 Payload (OrderCreatedPayload, OrderPaidPayload 공용)
 */
public record OrderItemPayload(
	Long productId,
	Long quantity
) {

	// 팩토리 메서드
	public static OrderItemPayload of(Long productId, Long quantity) {
		return new OrderItemPayload(productId, quantity);
	}

}
