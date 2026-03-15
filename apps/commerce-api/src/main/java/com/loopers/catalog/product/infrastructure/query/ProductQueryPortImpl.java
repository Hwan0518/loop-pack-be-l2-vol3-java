package com.loopers.catalog.product.infrastructure.query;


import com.loopers.catalog.product.application.dto.out.AdminProductDetailOutDto;
import com.loopers.catalog.product.application.dto.out.AdminProductOutDto;
import com.loopers.catalog.product.application.dto.out.ProductOutDto;
import com.loopers.catalog.product.application.port.out.query.ProductQueryPort;
import com.loopers.catalog.product.application.port.out.query.criteria.ProductSearchCriteria;
import com.loopers.catalog.product.domain.repository.vo.PageCriteria;
import com.loopers.catalog.product.domain.repository.vo.PageResult;
import com.loopers.catalog.product.infrastructure.cache.dto.IdListCacheEntry;
import com.loopers.catalog.product.infrastructure.cache.dto.ProductCacheDto;
import com.loopers.catalog.product.infrastructure.querydsl.ProductQuerydslRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;


@Repository
@RequiredArgsConstructor
public class ProductQueryPortImpl implements ProductQueryPort {

	// querydsl
	private final ProductQuerydslRepository productQuerydslRepository;


	/**
	 * 상품 복잡 조회 포트 구현체 (ProductQuerydslRepository에 위임)
	 * 1. 사용자 상품 검색 (활성 상품만)
	 * 2. 관리자 상품 검색 (전체 상품)
	 * 3. 상품 ID 리스트 검색 (캐시 write-through용)
	 * 4. 단건 상품 캐시 DTO 조회
	 * 5. 다건 상품 캐시 DTO 조회
	 * 6. 관리자 상품 상세 조회 (삭제 포함)
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


	// 3. 상품 ID 리스트 검색 (캐시 write-through용)
	@Override
	public IdListCacheEntry searchProductIds(ProductSearchCriteria criteria, PageCriteria pageCriteria) {
		return productQuerydslRepository.searchProductIds(criteria, pageCriteria);
	}


	// 4. 단건 상품 캐시 DTO 조회 (Read Model projection)
	@Override
	public ProductCacheDto findProductCacheDtoById(Long productId) {
		return productQuerydslRepository.findProductCacheDtoById(productId);
	}


	// 5. 다건 상품 캐시 DTO 조회 (Read Model bulk projection)
	@Override
	public List<ProductCacheDto> findProductCacheDtosByIds(List<Long> productIds) {
		return productQuerydslRepository.findProductCacheDtosByIds(productIds);
	}


	// 6. 관리자 상품 상세 조회 (삭제 포함, Read Model projection)
	@Override
	public AdminProductDetailOutDto findAdminProductDetailById(Long productId) {
		return productQuerydslRepository.findAdminProductDetailById(productId);
	}

}
