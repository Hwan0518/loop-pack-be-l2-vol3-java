package com.loopers.catalog.product.interfaces.web.controller;


import com.loopers.catalog.product.application.dto.out.AdminProductDetailOutDto;
import com.loopers.catalog.product.application.dto.out.AdminProductPageOutDto;
import com.loopers.catalog.product.application.facade.ProductQueryFacade;
import com.loopers.catalog.product.domain.model.enums.ProductSortType;
import com.loopers.catalog.product.interfaces.web.response.AdminProductDetailResponse;
import com.loopers.catalog.product.interfaces.web.response.AdminProductPageResponse;
import com.loopers.support.common.AdminHeaderValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api-admin/v1/products")
@RequiredArgsConstructor
public class ProductAdminQueryController {

	// facade
	private final ProductQueryFacade productQueryFacade;


	/**
	 * 상품 관리자 조회 컨트롤러 (LDAP 인증 필요)
	 * 1. 상품 목록 조회 (관리자, 전체 상품)
	 * 2. 상품 상세 조회 (관리자, 삭제 포함)
	 */

	// 1. 상품 목록 조회 (관리자, 전체 상품)
	@GetMapping
	public ResponseEntity<AdminProductPageResponse> getAdminProducts(
		@RequestHeader(value = "X-Loopers-Ldap", required = false) String ldapHeader,
		@RequestParam(required = false) Long brandId,
		@RequestParam(required = false) ProductSortType sortType,
		@RequestParam(defaultValue = "0") int page,
		@RequestParam(defaultValue = "20") int size
	) {

		// LDAP 인증
		AdminHeaderValidator.validate(ldapHeader);

		// 관리자 상품 목록 조회
		AdminProductPageOutDto result = productQueryFacade.getAdminProducts(brandId, sortType, page, size);

		// 응답 변환
		AdminProductPageResponse response = AdminProductPageResponse.from(result);

		// 200 OK 반환
		return ResponseEntity.ok(response);
	}


	// 2. 상품 상세 조회 (관리자, 삭제 포함)
	@GetMapping("/{productId}")
	public ResponseEntity<AdminProductDetailResponse> getAdminProduct(
		@RequestHeader(value = "X-Loopers-Ldap", required = false) String ldapHeader,
		@PathVariable Long productId
	) {

		// LDAP 인증
		AdminHeaderValidator.validate(ldapHeader);

		// 관리자 상품 상세 조회
		AdminProductDetailOutDto outDto = productQueryFacade.getAdminProduct(productId);

		// 응답 변환
		AdminProductDetailResponse response = AdminProductDetailResponse.from(outDto);

		// 200 OK 반환
		return ResponseEntity.ok(response);
	}

}
