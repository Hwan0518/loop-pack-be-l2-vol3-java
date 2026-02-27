package com.loopers.catalog.brand.application.service;


import com.loopers.catalog.brand.application.dto.out.AdminBrandOutDto;
import com.loopers.catalog.brand.application.dto.out.AdminBrandPageOutDto;
import com.loopers.catalog.brand.application.dto.out.BrandOutDto;
import com.loopers.catalog.brand.application.dto.out.BrandPageOutDto;
import com.loopers.catalog.brand.domain.model.Brand;
import com.loopers.catalog.brand.domain.model.enums.VisibleStatus;
import com.loopers.catalog.brand.domain.repository.BrandQueryRepository;
import com.loopers.catalog.brand.domain.repository.vo.PageCriteria;
import com.loopers.catalog.brand.domain.repository.vo.PageResult;
import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class BrandQueryService {

	// repository
	private final BrandQueryRepository brandQueryRepository;


	/**
	 * 브랜드 조회 서비스
	 * 1. ID로 브랜드 조회
	 * 2. 브랜드 목록 조회 (페이지네이션)
	 * 3. 브랜드 페이지 조회 (DTO 변환 포함)
	 * 4. ID로 노출 브랜드 조회 (VISIBLE만)
	 * 5. 노출 브랜드 페이지 조회 (VISIBLE만, DTO 변환 포함)
	 * 6. 관리자 브랜드 페이지 조회 (필터 옵션, DTO 변환 포함)
	 */

	// 1. ID로 브랜드 조회
	@Transactional(readOnly = true)
	public Brand getBrandById(Long id) {
		return brandQueryRepository.findById(id)
			.orElseThrow(() -> new CoreException(ErrorType.BRAND_NOT_FOUND));
	}


	// 2. 브랜드 목록 조회 (페이지네이션)
	@Transactional(readOnly = true)
	public PageResult<Brand> getBrands(PageCriteria pageCriteria) {
		return brandQueryRepository.findAll(pageCriteria);
	}


	// 3. 브랜드 페이지 조회 (DTO 변환 포함, Facade용)
	@Transactional(readOnly = true)
	public BrandPageOutDto getBrandsAsPage(int page, int size) {

		// 브랜드 목록 조회
		PageResult<Brand> result = brandQueryRepository.findAll(new PageCriteria(page, size));

		// DTO 변환
		return new BrandPageOutDto(
			result.content().stream().map(BrandOutDto::from).toList(),
			result.page(),
			result.size(),
			result.totalElements()
		);
	}


	// 4. ID로 노출 브랜드 조회 (VISIBLE만)
	@Transactional(readOnly = true)
	public Brand getVisibleBrandById(Long id) {
		return brandQueryRepository.findVisibleById(id)
			.orElseThrow(() -> new CoreException(ErrorType.BRAND_NOT_FOUND));
	}


	// 5. 노출 브랜드 페이지 조회 (VISIBLE만, DTO 변환 포함)
	@Transactional(readOnly = true)
	public BrandPageOutDto getVisibleBrandsAsPage(int page, int size) {

		// VISIBLE 브랜드 목록 조회
		PageResult<Brand> result = brandQueryRepository.findAllVisible(new PageCriteria(page, size));

		// DTO 변환
		return new BrandPageOutDto(
			result.content().stream().map(BrandOutDto::from).toList(),
			result.page(),
			result.size(),
			result.totalElements()
		);
	}


	// 6. 관리자 브랜드 페이지 조회 (필터 옵션, DTO 변환 포함)
	@Transactional(readOnly = true)
	public AdminBrandPageOutDto getAdminBrandsAsPage(VisibleStatus visibleStatus, int page, int size) {

		// 조회 (null이면 전체, 아니면 상태 필터)
		PageResult<Brand> result;
		if (visibleStatus == null) {
			result = brandQueryRepository.findAll(new PageCriteria(page, size));
		} else {
			result = brandQueryRepository.findAllByVisibleStatus(visibleStatus, new PageCriteria(page, size));
		}

		// DTO 변환
		return new AdminBrandPageOutDto(
			result.content().stream().map(AdminBrandOutDto::from).toList(),
			result.page(),
			result.size(),
			result.totalElements()
		);
	}

}
