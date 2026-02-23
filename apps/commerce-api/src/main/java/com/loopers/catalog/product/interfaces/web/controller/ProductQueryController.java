package com.loopers.catalog.product.interfaces.web.controller;


import com.loopers.catalog.product.application.dto.out.ProductDetailOutDto;
import com.loopers.catalog.product.application.dto.out.ProductPageOutDto;
import com.loopers.catalog.product.application.facade.ProductQueryFacade;
import com.loopers.catalog.product.domain.model.enums.ProductSortType;
import com.loopers.catalog.product.interfaces.web.response.ProductDetailResponse;
import com.loopers.catalog.product.interfaces.web.response.ProductPageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductQueryController {

	// facade
	private final ProductQueryFacade productQueryFacade;


	/**
	 * 상품 조회 컨트롤러 (인증 불필요)
	 * 1. 상품 목록 검색
	 * 2. 상품 상세 조회
	 */

	// 1. 상품 목록 검색
	@GetMapping
	public ResponseEntity<ProductPageResponse> getProducts(
		@RequestParam(required = false) Long brandId,
		@RequestParam(required = false) ProductSortType sortType,
		@RequestParam(defaultValue = "0") int page,
		@RequestParam(defaultValue = "20") int size
	) {

		// 상품 목록 검색
		ProductPageOutDto result = productQueryFacade.getProducts(brandId, sortType, page, size);

		// 응답 변환
		ProductPageResponse response = ProductPageResponse.from(result);

		// 200 OK 반환
		return ResponseEntity.ok(response);
	}


	// 2. 상품 상세 조회
	@GetMapping("/{productId}")
	public ResponseEntity<ProductDetailResponse> getProduct(@PathVariable Long productId) {

		// 상품 상세 조회
		ProductDetailOutDto outDto = productQueryFacade.getProduct(productId);

		// 응답 변환
		ProductDetailResponse response = ProductDetailResponse.from(outDto);

		// 200 OK 반환
		return ResponseEntity.ok(response);
	}

}
