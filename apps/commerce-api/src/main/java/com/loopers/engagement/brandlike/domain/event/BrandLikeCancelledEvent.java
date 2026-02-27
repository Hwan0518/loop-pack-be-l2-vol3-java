package com.loopers.engagement.brandlike.domain.event;

/**
 * 브랜드 좋아요 취소 이벤트
 * - brandId: 좋아요 취소 대상 브랜드 ID
 */
public record BrandLikeCancelledEvent(Long brandId) {
}
