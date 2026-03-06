package com.loopers.ordering.order.application.dto.in;


import java.util.List;


/**
 * 주문 생성 입력 DTO
 * - cartItemIds: 주문할 장바구니 항목 ID 목록
 * - requestId: 멱등성 보장용 요청 ID
 * - couponId: 적용할 발급 쿠폰 ID (nullable — 쿠폰 미사용 시 null)
 */
public record OrderCreateInDto(List<Long> cartItemIds, String requestId, Long couponId) {
}
