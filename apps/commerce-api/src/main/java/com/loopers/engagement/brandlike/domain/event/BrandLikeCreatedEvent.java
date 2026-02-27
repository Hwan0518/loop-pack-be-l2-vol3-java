package com.loopers.engagement.brandlike.domain.event;

/**
 * 브랜드 좋아요 생성 이벤트
 * - brandId: 좋아요 대상 브랜드 ID
 */
public record BrandLikeCreatedEvent(Long brandId) {
}
