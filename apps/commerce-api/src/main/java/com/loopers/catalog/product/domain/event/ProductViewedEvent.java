package com.loopers.catalog.product.domain.event;


import java.time.LocalDateTime;


/**
 * 상품 상세 조회 이벤트 (userId nullable - 비로그인 조회 포함)
 * @subscriber MetricsCollectorConsumer - 조회 수 집계 반영
 * @subscriber UserActionLogConsumer - 사용자 액션 로그 저장
 * @kafka PRODUCT_VIEWED → MetricsCollectorConsumer (view_count), UserActionLogConsumer
 */
public record ProductViewedEvent(
	Long userId,
	Long productId,
	LocalDateTime occurredAt
) {

	// 팩토리 메서드 — userId(nullable) + productId로 생성
	public static ProductViewedEvent of(Long userId, Long productId) {
		return new ProductViewedEvent(userId, productId, LocalDateTime.now());
	}

}
