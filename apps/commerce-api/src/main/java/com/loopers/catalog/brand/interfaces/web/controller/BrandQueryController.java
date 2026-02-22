package com.loopers.catalog.brand.interfaces.web.controller;


import com.loopers.catalog.brand.application.dto.out.BrandDetailOutDto;
import com.loopers.catalog.brand.application.dto.out.BrandPageOutDto;
import com.loopers.catalog.brand.application.facade.BrandQueryFacade;
import com.loopers.catalog.brand.interfaces.web.response.BrandDetailResponse;
import com.loopers.catalog.brand.interfaces.web.response.BrandPageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/brands")
@RequiredArgsConstructor
public class BrandQueryController {

	// facade
	private final BrandQueryFacade brandQueryFacade;


	/**
	 * 브랜드 조회 컨트롤러 (인증 불필요)
	 * 1. 브랜드 목록 조회
	 * 2. 브랜드 상세 조회
	 */

	// 1. 브랜드 목록 조회
	@GetMapping
	public ResponseEntity<BrandPageResponse> getBrands(
		@RequestParam(defaultValue = "0") int page,
		@RequestParam(defaultValue = "20") int size
	) {

		// 브랜드 목록 조회
		BrandPageOutDto result = brandQueryFacade.getBrands(page, size);

		// 응답 변환
		BrandPageResponse response = BrandPageResponse.from(result);

		// 200 OK 반환
		return ResponseEntity.ok(response);
	}


	// 2. 브랜드 상세 조회
	@GetMapping("/{brandId}")
	public ResponseEntity<BrandDetailResponse> getBrand(@PathVariable Long brandId) {

		// 브랜드 상세 조회
		BrandDetailOutDto outDto = brandQueryFacade.getBrand(brandId);

		// 응답 변환
		BrandDetailResponse response = BrandDetailResponse.from(outDto);

		// 200 OK 반환
		return ResponseEntity.ok(response);
	}

}
