package com.loopers.ordering.order.application.dto.out;


import com.loopers.ordering.order.domain.model.Order;
import com.loopers.support.common.event.ordering.OrderItemPayload;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;


/**
 * Kafka 발행용 주문 생성 Payload
 */
public record OrderCreatedPayload(
	Long orderId,
	Long userId,
	String requestId,
	List<OrderItemPayload> items,
	BigDecimal totalPrice,
	LocalDateTime occurredAt
) {

	// 팩토리 메서드 — Order 도메인 모델로 생성
	public static OrderCreatedPayload from(Order order) {
		List<OrderItemPayload> items = order.getItems().stream()
			.map(item -> new OrderItemPayload(item.getProductId(), item.getQuantity()))
			.toList();
		return new OrderCreatedPayload(
			order.getId(), order.getUserId(), order.getRequestId(),
			items, order.getTotalPrice(), LocalDateTime.now()
		);
	}

}
