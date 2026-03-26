package com.loopers.catalog.product.application.facade;


import com.loopers.catalog.product.application.dto.out.*;
import com.loopers.catalog.product.application.service.ProductQueryService;
import com.loopers.catalog.product.domain.event.ProductViewedEvent;
import com.loopers.catalog.product.domain.model.Product;
import com.loopers.catalog.product.domain.model.enums.ProductSortType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


@Service
@RequiredArgsConstructor
public class ProductQueryFacade {

	// service
	private final ProductQueryService productQueryService;
	// event
	private final ApplicationEventPublisher eventPublisher;


	/**
	 * 상품 조회 파사드
	 * 1. 사용자 상품 상세 조회 (활성 상품만, 브랜드명 포함)
	 * 2. 사용자 상품 목록 검색 (브랜드 필터, 정렬, 페이지네이션)
	 * 3. 관리자 상품 상세 조회 (삭제 포함, Read Model 기반 — 삭제된 브랜드에도 안전)
	 * 4. 관리자 상품 목록 검색 (전체 상품)
	 * 5. 활성 상품 조회 (Cross-BC 전용 — ACL에서 호출)
	 * 6. 활성 상품 일괄 조회 (Cross-BC 전용 — ACL에서 호출)
	 */

	// 1. 사용자 상품 상세 조회 (캐시 적용 — PER + 스탬피드 보호, Read Model projection 기반)
	@Transactional
	public ProductDetailOutDto getProduct(Long id, Long userId) {

		// Read Model에서 ProductCacheDto → ProductDetailOutDto 변환 (캐시 적용)
		ProductDetailOutDto result = productQueryService.getOrLoadProductDetail(id);

		// 상품 조회 이벤트 발행 (userId nullable — 비로그인 조회 포함)
		// → [UserActionEventListener] VIEW 로깅 (Step 1), Step 2 이후 제거
		eventPublisher.publishEvent(ProductViewedEvent.of(userId, id));

		return result;
	}


	// 2. 사용자 상품 목록 검색
	@Transactional(readOnly = true)
	public ProductPageOutDto getProducts(Long brandId, ProductSortType sortType, int page, int size) {
		return productQueryService.searchProducts(brandId, sortType, page, size);
	}


	// 3. 관리자 상품 상세 조회 (Read Model 기반 — 삭제된 브랜드에도 비정규화된 brandName 사용)
	@Transactional(readOnly = true)
	public AdminProductDetailOutDto getAdminProduct(Long id) {
		return productQueryService.getAdminProductDetail(id);
	}


	// 4. 관리자 상품 목록 검색
	@Transactional(readOnly = true)
	public AdminProductPageOutDto getAdminProducts(Long brandId, ProductSortType sortType, int page, int size) {
		return productQueryService.searchAdminProducts(brandId, sortType, page, size);
	}


	// 5. 활성 상품 조회 (Cross-BC 전용 — ACL에서 호출)
	@Transactional(readOnly = true)
	public Product findActiveById(Long productId) {
		return productQueryService.findActiveById(productId);
	}


	// 6. 활성 상품 일괄 조회 (Cross-BC 전용 — ACL에서 호출)
	@Transactional(readOnly = true)
	public List<Product> findActiveByIds(List<Long> ids) {
		return productQueryService.findActiveByIds(ids);
	}

}
