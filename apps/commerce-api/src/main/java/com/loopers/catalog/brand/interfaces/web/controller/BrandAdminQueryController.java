package com.loopers.catalog.brand.interfaces.web.controller;


import com.loopers.catalog.brand.application.dto.out.BrandAdminDetailOutDto;
import com.loopers.catalog.brand.application.dto.out.BrandAdminPageOutDto;
import com.loopers.catalog.brand.application.facade.BrandQueryFacade;
import com.loopers.catalog.brand.domain.model.enums.VisibleStatus;
import com.loopers.catalog.brand.interfaces.web.response.BrandAdminDetailResponse;
import com.loopers.catalog.brand.interfaces.web.response.BrandAdminPageResponse;
import com.loopers.support.common.AdminHeaderValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api-admin/v1/brands")
@RequiredArgsConstructor
public class BrandAdminQueryController {

	// facade
	private final BrandQueryFacade brandQueryFacade;


	/**
	 * 브랜드 관리자 조회 컨트롤러 (LDAP 인증 필요)
	 * 1. 브랜드 목록 조회 (관리자, 노출상태 필터 옵션)
	 * 2. 브랜드 상세 조회 (관리자, HIDDEN 포함)
	 */

	// 1. 브랜드 목록 조회 (관리자, 노출상태 필터 옵션)
	@GetMapping
	public ResponseEntity<BrandAdminPageResponse> getBrands(
		@RequestHeader(value = "X-Loopers-Ldap", required = false) String ldapHeader,
		@RequestParam(required = false) VisibleStatus visibleStatus,
		@RequestParam(defaultValue = "0") int page,
		@RequestParam(defaultValue = "20") int size
	) {

		// LDAP 인증
		AdminHeaderValidator.validate(ldapHeader);

		// 브랜드 목록 조회
		BrandAdminPageOutDto result = brandQueryFacade.getAdminBrands(visibleStatus, page, size);

		// 응답 변환
		BrandAdminPageResponse response = BrandAdminPageResponse.from(result);

		// 200 OK 반환
		return ResponseEntity.ok(response);
	}


	// 2. 브랜드 상세 조회 (관리자, HIDDEN 포함)
	@GetMapping("/{brandId}")
	public ResponseEntity<BrandAdminDetailResponse> getBrand(
		@RequestHeader(value = "X-Loopers-Ldap", required = false) String ldapHeader,
		@PathVariable Long brandId
	) {

		// LDAP 인증
		AdminHeaderValidator.validate(ldapHeader);

		// 브랜드 상세 조회
		BrandAdminDetailOutDto outDto = brandQueryFacade.getAdminBrand(brandId);

		// 응답 변환
		BrandAdminDetailResponse response = BrandAdminDetailResponse.from(outDto);

		// 200 OK 반환
		return ResponseEntity.ok(response);
	}

}
