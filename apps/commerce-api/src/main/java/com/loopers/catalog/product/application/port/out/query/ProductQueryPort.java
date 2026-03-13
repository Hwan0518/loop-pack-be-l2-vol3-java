package com.loopers.catalog.product.application.port.out.query;


import com.loopers.catalog.product.application.dto.out.AdminProductDetailOutDto;
import com.loopers.catalog.product.application.dto.out.AdminProductOutDto;
import com.loopers.catalog.product.application.dto.out.ProductOutDto;
import com.loopers.catalog.product.application.port.out.query.criteria.ProductSearchCriteria;
import com.loopers.catalog.product.domain.repository.vo.PageCriteria;
import com.loopers.catalog.product.domain.repository.vo.PageResult;
import com.loopers.catalog.product.infrastructure.cache.dto.IdListCacheEntry;
import com.loopers.catalog.product.infrastructure.cache.dto.ProductCacheDto;

import java.util.List;


public interface ProductQueryPort {

	/**
	 * 상품 복잡 조회 포트
	 * 1. 사용자 상품 검색 (활성 상품만, 브랜드 필터, 정렬, 페이지네이션)
	 * 2. 관리자 상품 검색 (전체 상품, 브랜드 필터, 정렬, 페이지네이션)
	 * 3. 상품 ID 리스트 검색 (캐시 write-through용)
	 * 4. 단건 상품 캐시 DTO 조회 (Read Model projection)
	 * 5. 다건 상품 캐시 DTO 조회 (Read Model bulk projection)
	 * 6. 관리자 상품 상세 조회 (삭제 포함, Read Model projection)
	 */

	// 1. 사용자 상품 검색
	PageResult<ProductOutDto> searchProducts(ProductSearchCriteria criteria, PageCriteria pageCriteria);

	// 2. 관리자 상품 검색
	PageResult<AdminProductOutDto> searchAdminProducts(ProductSearchCriteria criteria, PageCriteria pageCriteria);

	// 3. 상품 ID 리스트 검색 (캐시 write-through용, 정렬 + 페이지네이션)
	IdListCacheEntry searchProductIds(ProductSearchCriteria criteria, PageCriteria pageCriteria);

	// 4. 단건 상품 캐시 DTO 조회 (Read Model projection)
	ProductCacheDto findProductCacheDtoById(Long productId);

	// 5. 다건 상품 캐시 DTO 조회 (Read Model bulk projection)
	List<ProductCacheDto> findProductCacheDtosByIds(List<Long> productIds);

	// 6. 관리자 상품 상세 조회 (삭제 포함, Read Model projection)
	AdminProductDetailOutDto findAdminProductDetailById(Long productId);

}
