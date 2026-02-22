package com.loopers.catalog.brand.interfaces.web.controller;


import com.loopers.catalog.brand.application.dto.in.BrandCreateInDto;
import com.loopers.catalog.brand.application.dto.in.BrandUpdateInDto;
import com.loopers.catalog.brand.application.dto.in.BrandVisibleStatusUpdateInDto;
import com.loopers.catalog.brand.application.dto.out.BrandAdminDetailOutDto;
import com.loopers.catalog.brand.application.facade.BrandCommandFacade;
import com.loopers.catalog.brand.interfaces.web.request.BrandCreateRequest;
import com.loopers.catalog.brand.interfaces.web.request.BrandUpdateRequest;
import com.loopers.catalog.brand.interfaces.web.request.BrandVisibleStatusUpdateRequest;
import com.loopers.catalog.brand.interfaces.web.response.BrandAdminDetailResponse;
import com.loopers.support.common.AdminHeaderValidator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api-admin/v1/brands")
@RequiredArgsConstructor
public class BrandAdminCommandController {

	// facade
	private final BrandCommandFacade brandCommandFacade;


	/**
	 * 브랜드 관리자 명령 컨트롤러 (LDAP 인증 필요)
	 * 1. 브랜드 생성
	 * 2. 브랜드 수정
	 * 3. 브랜드 삭제
	 * 4. 브랜드 노출 상태 변경
	 */

	// 1. 브랜드 생성
	@PostMapping
	public ResponseEntity<BrandAdminDetailResponse> createBrand(
		@RequestHeader(value = "X-Loopers-Ldap", required = false) String ldapHeader,
		@Valid @RequestBody BrandCreateRequest request
	) {

		// LDAP 인증
		AdminHeaderValidator.validate(ldapHeader);

		// InDto 생성
		BrandCreateInDto inDto = new BrandCreateInDto(request.name(), request.description());

		// 브랜드 생성
		BrandAdminDetailOutDto outDto = brandCommandFacade.createBrand(inDto);

		// 응답 변환
		BrandAdminDetailResponse response = BrandAdminDetailResponse.from(outDto);

		// 201 Created 반환
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}


	// 2. 브랜드 수정
	@PutMapping("/{brandId}")
	public ResponseEntity<BrandAdminDetailResponse> updateBrand(
		@RequestHeader(value = "X-Loopers-Ldap", required = false) String ldapHeader,
		@PathVariable Long brandId,
		@Valid @RequestBody BrandUpdateRequest request
	) {

		// LDAP 인증
		AdminHeaderValidator.validate(ldapHeader);

		// InDto 생성
		BrandUpdateInDto inDto = new BrandUpdateInDto(
			request.name(), request.description(), request.visibleStatus()
		);

		// 브랜드 수정
		BrandAdminDetailOutDto outDto = brandCommandFacade.updateBrand(brandId, inDto);

		// 응답 변환
		BrandAdminDetailResponse response = BrandAdminDetailResponse.from(outDto);

		// 200 OK 반환
		return ResponseEntity.ok(response);
	}


	// 3. 브랜드 삭제
	@DeleteMapping("/{brandId}")
	public ResponseEntity<Void> deleteBrand(
		@RequestHeader(value = "X-Loopers-Ldap", required = false) String ldapHeader,
		@PathVariable Long brandId
	) {

		// LDAP 인증
		AdminHeaderValidator.validate(ldapHeader);

		// 브랜드 삭제
		brandCommandFacade.deleteBrand(brandId);

		// 204 No Content 반환
		return ResponseEntity.noContent().build();
	}


	// 4. 브랜드 노출 상태 변경
	@PatchMapping("/{brandId}/visible-status")
	public ResponseEntity<BrandAdminDetailResponse> updateVisibleStatus(
		@RequestHeader(value = "X-Loopers-Ldap", required = false) String ldapHeader,
		@PathVariable Long brandId,
		@Valid @RequestBody BrandVisibleStatusUpdateRequest request
	) {

		// LDAP 인증
		AdminHeaderValidator.validate(ldapHeader);

		// InDto 생성
		BrandVisibleStatusUpdateInDto inDto = new BrandVisibleStatusUpdateInDto(request.visibleStatus());

		// 노출 상태 변경
		BrandAdminDetailOutDto outDto = brandCommandFacade.updateVisibleStatus(brandId, inDto);

		// 응답 변환
		BrandAdminDetailResponse response = BrandAdminDetailResponse.from(outDto);

		// 200 OK 반환
		return ResponseEntity.ok(response);
	}

}
