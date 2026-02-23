package com.loopers.catalog.product.application.port.out.query;


import com.loopers.catalog.product.application.dto.out.AdminProductOutDto;
import com.loopers.catalog.product.application.dto.out.ProductOutDto;
import com.loopers.catalog.product.application.port.out.query.criteria.ProductSearchCriteria;
import com.loopers.catalog.product.domain.repository.vo.PageCriteria;
import com.loopers.catalog.product.domain.repository.vo.PageResult;


public interface ProductQueryPort {

	/**
	 * 상품 복잡 조회 포트
	 * 1. 사용자 상품 검색 (활성 상품만, 브랜드 필터, 정렬, 페이지네이션)
	 * 2. 관리자 상품 검색 (전체 상품, 브랜드 필터, 정렬, 페이지네이션)
	 */

	// 1. 사용자 상품 검색
	PageResult<ProductOutDto> searchProducts(ProductSearchCriteria criteria, PageCriteria pageCriteria);

	// 2. 관리자 상품 검색
	PageResult<AdminProductOutDto> searchAdminProducts(ProductSearchCriteria criteria, PageCriteria pageCriteria);

}
