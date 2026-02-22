package com.loopers.catalog.brand.domain.event;


/**
 * 브랜드 삭제 이벤트
 * - brandId: 삭제된 브랜드 ID
 */
public record BrandDeletedEvent(Long brandId) {
}
