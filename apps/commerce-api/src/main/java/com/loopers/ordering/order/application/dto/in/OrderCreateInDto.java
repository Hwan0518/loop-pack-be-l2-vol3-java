package com.loopers.ordering.order.application.dto.in;


import java.util.List;


/**
 * 주문 생성 입력 DTO
 * - cartItemIds: 주문할 장바구니 항목 ID 목록
 * - requestId: 멱등성 보장용 요청 ID
 */
public record OrderCreateInDto(List<Long> cartItemIds, String requestId) {
}
