package com.loopers.catalog.product.application.facade;


import com.loopers.catalog.brand.application.service.BrandQueryService;
import com.loopers.catalog.brand.domain.model.Brand;
import com.loopers.catalog.product.application.dto.out.AdminProductDetailOutDto;
import com.loopers.catalog.product.application.dto.out.AdminProductPageOutDto;
import com.loopers.catalog.product.application.dto.out.ProductDetailOutDto;
import com.loopers.catalog.product.application.dto.out.ProductPageOutDto;
import com.loopers.catalog.product.application.service.ProductQueryService;
import com.loopers.catalog.product.domain.model.Product;
import com.loopers.catalog.product.domain.model.enums.ProductSortType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class ProductQueryFacade {

	// service
	private final ProductQueryService productQueryService;
	private final BrandQueryService brandQueryService;


	/**
	 * 상품 조회 파사드
	 * 1. 사용자 상품 상세 조회 (활성 상품만, 브랜드명 포함)
	 * 2. 사용자 상품 목록 검색 (브랜드 필터, 정렬, 페이지네이션)
	 * 3. 관리자 상품 상세 조회 (삭제 포함, 브랜드명 포함)
	 * 4. 관리자 상품 목록 검색 (전체 상품)
	 */

	// 1. 사용자 상품 상세 조회
	@Transactional(readOnly = true)
	public ProductDetailOutDto getProduct(Long id) {

		// 활성 상품 조회
		Product product = productQueryService.findActiveById(id);

		// 브랜드 조회 (브랜드명 포함 응답)
		Brand brand = brandQueryService.getBrandById(product.getBrandId());

		// DTO 변환
		return ProductDetailOutDto.from(product, brand.getName().value());
	}


	// 2. 사용자 상품 목록 검색
	@Transactional(readOnly = true)
	public ProductPageOutDto getProducts(Long brandId, ProductSortType sortType, int page, int size) {
		return productQueryService.searchProducts(brandId, sortType, page, size);
	}


	// 3. 관리자 상품 상세 조회
	@Transactional(readOnly = true)
	public AdminProductDetailOutDto getAdminProduct(Long id) {

		// 상품 조회 (삭제 포함)
		Product product = productQueryService.findById(id);

		// 브랜드 조회 (브랜드명 포함 응답)
		Brand brand = brandQueryService.getBrandById(product.getBrandId());

		// DTO 변환
		return AdminProductDetailOutDto.from(product, brand.getName().value());
	}


	// 4. 관리자 상품 목록 검색
	@Transactional(readOnly = true)
	public AdminProductPageOutDto getAdminProducts(Long brandId, ProductSortType sortType, int page, int size) {
		return productQueryService.searchAdminProducts(brandId, sortType, page, size);
	}

}
