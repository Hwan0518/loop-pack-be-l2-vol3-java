package com.loopers.ordering.order.domain.event;


import com.loopers.ordering.order.domain.model.Order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;


/**
 * 주문 생성 완료 이벤트
 * @subscriber OrderEventListener - 장바구니 정리
 * @subscriber UserActionEventListener - 유저 행동 로깅 (ORDER)
 */
public record OrderCreatedEvent(
	Long orderId,
	Long userId,
	String requestId,
	List<Long> cartItemIds,
	List<OrderItemSnapshot> items,
	BigDecimal totalPrice,
	LocalDateTime occurredAt
) {

	public record OrderItemSnapshot(Long productId, Long quantity) {}


	// 팩토리 메서드 — Order 도메인 모델 + 장바구니 ID 목록으로 생성
	public static OrderCreatedEvent from(Order order, List<Long> cartItemIds) {
		List<OrderItemSnapshot> items = order.getItems().stream()
			.map(item -> new OrderItemSnapshot(item.getProductId(), item.getQuantity()))
			.toList();

		return new OrderCreatedEvent(
			order.getId(),
			order.getUserId(),
			order.getRequestId(),
			cartItemIds,
			items,
			order.getTotalPrice(),
			LocalDateTime.now()
		);
	}

}
