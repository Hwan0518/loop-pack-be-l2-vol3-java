package com.loopers.ordering.order.domain.event;


import java.util.List;


/**
 * 주문 생성 이벤트
 * - orderId: 주문 ID
 * - userId: 주문자 ID
 * - cartItemIds: 장바구니 항목 ID 목록 (주문 후 장바구니 정리용)
 */
public record OrderCreatedEvent(Long orderId, Long userId, List<Long> cartItemIds) {
}
