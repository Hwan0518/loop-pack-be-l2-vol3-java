package com.loopers.catalog.brand.application.facade;


import com.loopers.catalog.brand.application.dto.in.BrandCreateInDto;
import com.loopers.catalog.brand.application.dto.in.BrandUpdateInDto;
import com.loopers.catalog.brand.application.dto.in.BrandVisibleStatusUpdateInDto;
import com.loopers.catalog.brand.application.dto.out.BrandAdminDetailOutDto;
import com.loopers.catalog.brand.application.service.BrandCommandService;
import com.loopers.catalog.brand.application.service.BrandQueryService;
import com.loopers.catalog.brand.domain.model.Brand;
import com.loopers.catalog.product.application.service.ProductQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class BrandCommandFacade {

	// service
	private final BrandCommandService brandCommandService;
	private final BrandQueryService brandQueryService;
	private final ProductQueryService productQueryService;


	/**
	 * 브랜드 명령 파사드
	 * 1. 브랜드 생성
	 * 2. 브랜드 수정
	 * 3. 브랜드 삭제
	 * 4. 브랜드 노출 상태 변경
	 */

	// 1. 브랜드 생성
	@Transactional
	public BrandAdminDetailOutDto createBrand(BrandCreateInDto inDto) {

		// 브랜드 생성
		Brand savedBrand = brandCommandService.createBrand(inDto);

		// DTO 변환
		return BrandAdminDetailOutDto.from(savedBrand);
	}


	// 2. 브랜드 수정
	@Transactional
	public BrandAdminDetailOutDto updateBrand(Long id, BrandUpdateInDto inDto) {

		// 브랜드 조회
		Brand brand = brandQueryService.getBrandById(id);

		// 브랜드 수정
		Brand updatedBrand = brandCommandService.updateBrand(brand, inDto);

		// DTO 변환
		return BrandAdminDetailOutDto.from(updatedBrand);
	}


	// 3. 브랜드 삭제
	@Transactional
	public void deleteBrand(Long id) {

		// 브랜드 조회
		Brand brand = brandQueryService.getBrandById(id);

		// 활성 상품 존재 여부 확인
		boolean hasActiveProducts = productQueryService.existsActiveByBrandId(id);

		// 브랜드 삭제 (검증 + 삭제 + 이벤트 발행)
		brandCommandService.deleteBrand(brand, hasActiveProducts);
	}


	// 4. 브랜드 노출 상태 변경
	@Transactional
	public BrandAdminDetailOutDto updateVisibleStatus(Long id, BrandVisibleStatusUpdateInDto inDto) {

		// 브랜드 조회
		Brand brand = brandQueryService.getBrandById(id);

		// 노출 상태 변경
		Brand updatedBrand = brandCommandService.updateVisibleStatus(brand, inDto);

		// DTO 변환
		return BrandAdminDetailOutDto.from(updatedBrand);
	}

}
