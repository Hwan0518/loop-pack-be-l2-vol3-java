package com.loopers.engagement.brandlike.application.dto.out;

import com.loopers.engagement.brandlike.domain.model.BrandLike;

import java.time.LocalDateTime;


/**
 * 브랜드 좋아요 결과 DTO
 * - id: 좋아요 ID
 * - userId: 사용자 ID
 * - targetId: 브랜드 ID
 * - createdAt: 생성 일시
 */
public record BrandLikeOutDto(Long id, Long userId, Long targetId, LocalDateTime createdAt) {

	// 1. BrandLike 도메인 객체를 DTO로 변환
	public static BrandLikeOutDto from(BrandLike brandLike) {
		return new BrandLikeOutDto(
			brandLike.getId(),
			brandLike.getUserId(),
			brandLike.getTargetId(),
			brandLike.getCreatedAt()
		);
	}

}
