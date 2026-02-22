package com.loopers.catalog.brand.application.facade;


import com.loopers.catalog.brand.application.dto.out.BrandAdminDetailOutDto;
import com.loopers.catalog.brand.application.dto.out.BrandAdminPageOutDto;
import com.loopers.catalog.brand.application.dto.out.BrandDetailOutDto;
import com.loopers.catalog.brand.application.dto.out.BrandPageOutDto;
import com.loopers.catalog.brand.application.service.BrandQueryService;
import com.loopers.catalog.brand.domain.model.Brand;
import com.loopers.catalog.brand.domain.model.enums.VisibleStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class BrandQueryFacade {

	// service
	private final BrandQueryService brandQueryService;


	/**
	 * 브랜드 조회 파사드
	 * 1. 브랜드 목록 조회 (User, VISIBLE만)
	 * 2. 브랜드 상세 조회 (User, VISIBLE만)
	 * 3. 관리자 브랜드 목록 조회 (전체 + 선택적 필터)
	 * 4. 관리자 브랜드 상세 조회 (HIDDEN 포함)
	 */

	// 1. 브랜드 목록 조회 (User, VISIBLE만)
	@Transactional(readOnly = true)
	public BrandPageOutDto getBrands(int page, int size) {
		return brandQueryService.getVisibleBrandsAsPage(page, size);
	}


	// 2. 브랜드 상세 조회 (User, VISIBLE만)
	@Transactional(readOnly = true)
	public BrandDetailOutDto getBrand(Long id) {

		// VISIBLE 브랜드 조회
		Brand brand = brandQueryService.getVisibleBrandById(id);

		// DTO 변환
		return BrandDetailOutDto.from(brand);
	}


	// 3. 관리자 브랜드 목록 조회 (전체 + 선택적 필터)
	@Transactional(readOnly = true)
	public BrandAdminPageOutDto getAdminBrands(VisibleStatus visibleStatus, int page, int size) {
		return brandQueryService.getAdminBrandsAsPage(visibleStatus, page, size);
	}


	// 4. 관리자 브랜드 상세 조회 (HIDDEN 포함)
	@Transactional(readOnly = true)
	public BrandAdminDetailOutDto getAdminBrand(Long id) {

		// 브랜드 조회 (HIDDEN 포함)
		Brand brand = brandQueryService.getBrandById(id);

		// DTO 변환
		return BrandAdminDetailOutDto.from(brand);
	}

}
