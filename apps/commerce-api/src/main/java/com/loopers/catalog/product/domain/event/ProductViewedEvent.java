package com.loopers.catalog.product.domain.event;


import java.time.LocalDateTime;


/**
 * 상품 상세 조회 이벤트 (userId nullable - 비로그인 조회 포함)
 * @subscriber UserActionEventListener - 유저 행동 로깅 (VIEW) -- Step 2 이후 제거
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
