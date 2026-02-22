package com.loopers.catalog.brand.domain.repository;


import com.loopers.catalog.brand.domain.model.Brand;
import com.loopers.catalog.brand.domain.model.enums.VisibleStatus;
import com.loopers.catalog.brand.domain.repository.vo.PageCriteria;
import com.loopers.catalog.brand.domain.repository.vo.PageResult;

import java.util.Optional;


public interface BrandQueryRepository {

	/**
	 * 브랜드 조회 리포지토리
	 * 1. ID로 브랜드 조회
	 * 2. 브랜드 목록 조회 (페이지네이션)
	 * 3. ID로 노출 브랜드 조회 (VISIBLE + 미삭제)
	 * 4. 노출 브랜드 목록 조회 (VISIBLE만 페이지네이션)
	 * 5. 노출 상태별 브랜드 목록 조회 (특정 상태 필터)
	 */

	// 1. ID로 브랜드 조회
	Optional<Brand> findById(Long id);

	// 2. 브랜드 목록 조회 (페이지네이션)
	PageResult<Brand> findAll(PageCriteria pageCriteria);

	// 3. ID로 노출 브랜드 조회 (VISIBLE + 미삭제)
	Optional<Brand> findVisibleById(Long id);

	// 4. 노출 브랜드 목록 조회 (VISIBLE만 페이지네이션)
	PageResult<Brand> findAllVisible(PageCriteria pageCriteria);

	// 5. 노출 상태별 브랜드 목록 조회 (특정 상태 필터)
	PageResult<Brand> findAllByVisibleStatus(VisibleStatus visibleStatus, PageCriteria pageCriteria);

}
