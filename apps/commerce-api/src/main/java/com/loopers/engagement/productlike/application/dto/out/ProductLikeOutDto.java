package com.loopers.engagement.productlike.application.dto.out;

import com.loopers.engagement.productlike.domain.model.ProductLike;

import java.time.LocalDateTime;


/**
 * 상품 좋아요 결과 DTO
 * - id: 좋아요 ID
 * - userId: 사용자 ID
 * - targetId: 상품 ID
 * - createdAt: 생성 일시
 */
public record ProductLikeOutDto(Long id, Long userId, Long targetId, LocalDateTime createdAt) {

	// 1. ProductLike 도메인 객체를 DTO로 변환
	public static ProductLikeOutDto from(ProductLike productLike) {
		return new ProductLikeOutDto(
			productLike.getId(),
			productLike.getUserId(),
			productLike.getTargetId(),
			productLike.getCreatedAt()
		);
	}

}
