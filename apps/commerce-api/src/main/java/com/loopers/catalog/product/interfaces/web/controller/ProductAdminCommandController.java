package com.loopers.catalog.product.interfaces.web.controller;


import com.loopers.catalog.product.application.dto.in.AdminProductCreateInDto;
import com.loopers.catalog.product.application.dto.in.AdminProductUpdateInDto;
import com.loopers.catalog.product.application.dto.out.AdminProductDetailOutDto;
import com.loopers.catalog.product.application.facade.ProductCommandFacade;
import com.loopers.catalog.product.interfaces.web.request.AdminProductCreateRequest;
import com.loopers.catalog.product.interfaces.web.request.AdminProductUpdateRequest;
import com.loopers.catalog.product.interfaces.web.response.AdminProductDetailResponse;
import com.loopers.support.common.AdminHeaderValidator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api-admin/v1/products")
@RequiredArgsConstructor
public class ProductAdminCommandController {

	// facade
	private final ProductCommandFacade productCommandFacade;


	/**
	 * 상품 관리자 명령 컨트롤러 (LDAP 인증 필요)
	 * 1. 상품 생성
	 * 2. 상품 수정
	 * 3. 상품 삭제
	 */

	// 1. 상품 생성
	@PostMapping
	public ResponseEntity<AdminProductDetailResponse> createProduct(
		@RequestHeader(value = "X-Loopers-Ldap", required = false) String ldapHeader,
		@Valid @RequestBody AdminProductCreateRequest request
	) {

		// LDAP 인증
		AdminHeaderValidator.validate(ldapHeader);

		// InDto 생성
		AdminProductCreateInDto inDto = new AdminProductCreateInDto(
			request.brandId(), request.name(), request.price(), request.stock(), request.description()
		);

		// 상품 생성
		AdminProductDetailOutDto outDto = productCommandFacade.createProduct(inDto);

		// 응답 변환
		AdminProductDetailResponse response = AdminProductDetailResponse.from(outDto);

		// 201 Created 반환
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}


	// 2. 상품 수정
	@PutMapping("/{productId}")
	public ResponseEntity<AdminProductDetailResponse> updateProduct(
		@RequestHeader(value = "X-Loopers-Ldap", required = false) String ldapHeader,
		@PathVariable Long productId,
		@Valid @RequestBody AdminProductUpdateRequest request
	) {

		// LDAP 인증
		AdminHeaderValidator.validate(ldapHeader);

		// InDto 생성
		AdminProductUpdateInDto inDto = new AdminProductUpdateInDto(
			request.name(), request.price(), request.stock(), request.description()
		);

		// 상품 수정
		AdminProductDetailOutDto outDto = productCommandFacade.updateProduct(productId, inDto);

		// 응답 변환
		AdminProductDetailResponse response = AdminProductDetailResponse.from(outDto);

		// 200 OK 반환
		return ResponseEntity.ok(response);
	}


	// 3. 상품 삭제
	@DeleteMapping("/{productId}")
	public ResponseEntity<Void> deleteProduct(
		@RequestHeader(value = "X-Loopers-Ldap", required = false) String ldapHeader,
		@PathVariable Long productId
	) {

		// LDAP 인증
		AdminHeaderValidator.validate(ldapHeader);

		// 상품 삭제
		productCommandFacade.deleteProduct(productId);

		// 204 No Content 반환
		return ResponseEntity.noContent().build();
	}

}
