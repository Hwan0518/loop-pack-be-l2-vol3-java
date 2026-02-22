package com.loopers.catalog.brand.application.service;


import com.loopers.catalog.brand.application.dto.out.BrandAdminOutDto;
import com.loopers.catalog.brand.application.dto.out.BrandAdminPageOutDto;
import com.loopers.catalog.brand.domain.model.Brand;
import com.loopers.catalog.brand.domain.model.enums.VisibleStatus;
import com.loopers.catalog.brand.domain.repository.BrandQueryRepository;
import com.loopers.catalog.brand.domain.repository.vo.PageCriteria;
import com.loopers.catalog.brand.domain.repository.vo.PageResult;
import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class BrandQueryService {

	// repository
	private final BrandQueryRepository brandQueryRepository;


	/**
	 * 브랜드 조회 서비스
	 * 1. ID로 브랜드 조회
	 * 2. 관리자 브랜드 페이지 조회 (필터 옵션, DTO 변환 포함)
	 */

	// 1. ID로 브랜드 조회
	public Brand getBrandById(Long id) {
		return brandQueryRepository.findById(id)
			.orElseThrow(() -> new CoreException(ErrorType.BRAND_NOT_FOUND));
	}


	// 2. 관리자 브랜드 페이지 조회 (필터 옵션, DTO 변환 포함)
	public BrandAdminPageOutDto getAdminBrandsAsPage(VisibleStatus visibleStatus, int page, int size) {

		// 조회 (null이면 전체, 아니면 상태 필터)
		PageResult<Brand> result;
		if (visibleStatus == null) {
			result = brandQueryRepository.findAll(new PageCriteria(page, size));
		} else {
			result = brandQueryRepository.findAllByVisibleStatus(visibleStatus, new PageCriteria(page, size));
		}

		// DTO 변환
		return new BrandAdminPageOutDto(
			result.content().stream().map(BrandAdminOutDto::from).toList(),
			result.page(),
			result.size(),
			result.totalElements()
		);
	}

}
