package com.loopers.catalog.product.application.service;


import com.loopers.catalog.product.application.dto.out.*;
import com.loopers.catalog.product.application.port.out.query.ProductQueryPort;
import com.loopers.catalog.product.application.port.out.query.criteria.ProductSearchCriteria;
import com.loopers.catalog.product.domain.model.Product;
import com.loopers.catalog.product.domain.model.enums.ProductSortType;
import com.loopers.catalog.product.domain.repository.ProductQueryRepository;
import com.loopers.catalog.product.domain.repository.vo.PageCriteria;
import com.loopers.catalog.product.domain.repository.vo.PageResult;
import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class ProductQueryService {

	// repository
	private final ProductQueryRepository productQueryRepository;
	// port
	private final ProductQueryPort productQueryPort;


	/**
	 * 상품 조회 서비스
	 * 1. ID로 활성 상품 조회
	 * 2. 브랜드의 활성 상품 존재 여부 확인
	 * 3. ID로 상품 조회 (삭제 포함, 관리자용)
	 * 4. 사용자 상품 목록 검색 (QueryPort, 페이지네이션)
	 * 5. 관리자 상품 목록 검색 (QueryPort, 페이지네이션)
	 */

	// 1. ID로 활성 상품 조회
	public Product findActiveById(Long id) {
		return productQueryRepository.findActiveById(id)
			.orElseThrow(() -> new CoreException(ErrorType.PRODUCT_NOT_FOUND));
	}


	// 2. 브랜드의 활성 상품 존재 여부 확인
	public boolean existsActiveByBrandId(Long brandId) {
		return productQueryRepository.existsActiveByBrandId(brandId);
	}


	// 3. ID로 상품 조회 (삭제 포함, 관리자용)
	public Product findById(Long id) {
		return productQueryRepository.findById(id)
			.orElseThrow(() -> new CoreException(ErrorType.PRODUCT_NOT_FOUND));
	}


	// 4. 사용자 상품 목록 검색 (QueryPort, 페이지네이션)
	public ProductPageOutDto searchProducts(Long brandId, ProductSortType sortType, int page, int size) {

		// 검색 조건 생성
		ProductSearchCriteria criteria = new ProductSearchCriteria(brandId, sortType);

		// QueryPort를 통한 검색
		PageResult<ProductOutDto> result = productQueryPort.searchProducts(criteria, new PageCriteria(page, size));

		// DTO 변환
		return ProductPageOutDto.from(result);
	}


	// 5. 관리자 상품 목록 검색 (QueryPort, 페이지네이션)
	public AdminProductPageOutDto searchAdminProducts(Long brandId, ProductSortType sortType, int page, int size) {

		// 검색 조건 생성
		ProductSearchCriteria criteria = new ProductSearchCriteria(brandId, sortType);

		// QueryPort를 통한 검색
		PageResult<AdminProductOutDto> result = productQueryPort.searchAdminProducts(criteria, new PageCriteria(page, size));

		// DTO 변환
		return AdminProductPageOutDto.from(result);
	}

}
