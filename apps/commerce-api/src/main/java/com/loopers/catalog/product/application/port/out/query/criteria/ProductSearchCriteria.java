package com.loopers.catalog.product.application.port.out.query.criteria;


import com.loopers.catalog.product.domain.model.enums.ProductSortType;


/**
 * 상품 검색 조건
 * - brandId: 브랜드 ID (nullable = 전체 조회)
 * - sortType: 정렬 타입
 */
public record ProductSearchCriteria(Long brandId, ProductSortType sortType) {
}
