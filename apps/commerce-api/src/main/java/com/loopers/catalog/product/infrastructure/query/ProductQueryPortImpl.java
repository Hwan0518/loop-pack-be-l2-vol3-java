package com.loopers.catalog.product.infrastructure.query;


import com.loopers.catalog.product.application.dto.out.AdminProductOutDto;
import com.loopers.catalog.product.application.dto.out.ProductOutDto;
import com.loopers.catalog.product.application.port.out.query.ProductQueryPort;
import com.loopers.catalog.product.application.port.out.query.criteria.ProductSearchCriteria;
import com.loopers.catalog.product.domain.repository.vo.PageCriteria;
import com.loopers.catalog.product.domain.repository.vo.PageResult;
import com.loopers.catalog.product.infrastructure.querydsl.ProductQuerydslRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;


@Repository
@RequiredArgsConstructor
public class ProductQueryPortImpl implements ProductQueryPort {

	// querydsl
	private final ProductQuerydslRepository productQuerydslRepository;


	/**
	 * 상품 복잡 조회 포트 구현체 (ProductQuerydslRepository에 위임)
	 * 1. 사용자 상품 검색 (활성 상품만)
	 * 2. 관리자 상품 검색 (전체 상품)
	 */

	// 1. 사용자 상품 검색 (활성 상품만)
	@Override
	public PageResult<ProductOutDto> searchProducts(ProductSearchCriteria criteria, PageCriteria pageCriteria) {
		return productQuerydslRepository.searchProducts(criteria, pageCriteria);
	}


	// 2. 관리자 상품 검색 (전체 상품)
	@Override
	public PageResult<AdminProductOutDto> searchAdminProducts(ProductSearchCriteria criteria, PageCriteria pageCriteria) {
		return productQuerydslRepository.searchAdminProducts(criteria, pageCriteria);
	}

}
