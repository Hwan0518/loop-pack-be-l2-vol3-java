package com.loopers.engagement.productlike.domain.event;


import com.loopers.engagement.productlike.domain.model.ProductLike;

import java.time.LocalDateTime;


/**
 * 상품 좋아요 생성 이벤트
 * @subscriber MetricsCollectorConsumer - 좋아요 수 집계 반영
 * @subscriber UserActionLogConsumer - 사용자 액션 로그 저장
 * @kafka PRODUCT_LIKED → MetricsCollectorConsumer (like_count), UserActionLogConsumer
 */
public record ProductLikedEvent(
	Long productLikeId,
	Long userId,
	Long productId,
	LocalDateTime occurredAt
) {

	// 팩토리 메서드 — ProductLike 도메인 모델로 생성
	public static ProductLikedEvent from(ProductLike productLike) {
		return new ProductLikedEvent(
			productLike.getId(),
			productLike.getUserId(),
			productLike.getTargetId(),
			LocalDateTime.now()
		);
	}

}
