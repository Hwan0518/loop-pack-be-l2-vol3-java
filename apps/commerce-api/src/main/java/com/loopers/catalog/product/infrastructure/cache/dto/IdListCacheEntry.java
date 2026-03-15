package com.loopers.catalog.product.infrastructure.cache.dto;


import java.util.List;


/**
 * ID 리스트 캐시 값
 * - 2계층 캐시 아키텍처에서 목록 캐시(Layer 1)의 값
 * - ids: 해당 페이지의 상품 ID 목록
 * - totalElements: 전체 상품 수 (페이지네이션 응답용)
 */
public record IdListCacheEntry(List<Long> ids, long totalElements) {
}
