package com.loopers.catalog.product.domain.repository.vo;


/**
 * 페이지네이션 조건
 * - page: 페이지 번호 (0-based)
 * - size: 페이지 크기
 */
public record PageCriteria(int page, int size) {
}
