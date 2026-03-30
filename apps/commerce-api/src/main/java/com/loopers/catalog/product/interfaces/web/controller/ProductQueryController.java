package com.loopers.catalog.product.interfaces.web.controller;


import com.loopers.catalog.product.application.dto.out.ProductDetailOutDto;
import com.loopers.catalog.product.application.dto.out.ProductPageOutDto;
import com.loopers.catalog.product.application.facade.ProductQueryFacade;
import com.loopers.catalog.product.domain.model.enums.ProductSortType;
import com.loopers.catalog.product.interfaces.web.response.ProductDetailResponse;
import com.loopers.catalog.product.interfaces.web.response.ProductPageResponse;
import com.loopers.support.common.auth.AuthenticationResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductQueryController {

	// facade
	private final ProductQueryFacade productQueryFacade;
	// auth
	private final AuthenticationResolver authenticationResolver;


	/**
	 * 상품 조회 컨트롤러
	 * 1. 상품 목록 검색 (인증 불필요)
	 * 2. 상품 상세 조회 (선택적 인증 — 비로그인 시 userId=null)
	 */

	// 1. 상품 목록 검색
	@GetMapping
	public ResponseEntity<ProductPageResponse> getProducts(
		@RequestParam(required = false) Long brandId,
		@RequestParam(value = "sort", required = false) ProductSortType sort,
		@RequestParam(defaultValue = "0") int page,
		@RequestParam(defaultValue = "20") int size
	) {

		// 상품 목록 검색
		ProductPageOutDto result = productQueryFacade.getProducts(brandId, sort, page, size);

		// 응답 변환
		ProductPageResponse response = ProductPageResponse.from(result);

		// 200 OK 반환
		return ResponseEntity.ok(response);
	}


	// 2. 상품 상세 조회 (선택적 인증 — 비로그인 시 userId=null, VIEW 이벤트 발행)
	@GetMapping("/{productId}")
	public ResponseEntity<ProductDetailResponse> getProduct(
		@PathVariable Long productId,
		@RequestHeader(value = "X-Loopers-LoginId", required = false) String loginId,
		@RequestHeader(value = "X-Loopers-LoginPw", required = false) String password
	) {

		// 선택적 인증 (비로그인 시 null)
		Long userId = authenticationResolver.resolveOptional(loginId, password);

		// 상품 상세 조회 (VIEW 이벤트 발행 포함)
		ProductDetailOutDto outDto = productQueryFacade.getProduct(productId, userId);

		// 응답 변환
		ProductDetailResponse response = ProductDetailResponse.from(outDto);

		// 200 OK 반환
		return ResponseEntity.ok(response);
	}

}
