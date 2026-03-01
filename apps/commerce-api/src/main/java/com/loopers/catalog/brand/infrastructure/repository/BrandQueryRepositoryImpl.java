package com.loopers.catalog.brand.infrastructure.repository;


import com.loopers.catalog.brand.domain.model.Brand;
import com.loopers.catalog.brand.domain.model.enums.VisibleStatus;
import com.loopers.catalog.brand.domain.repository.BrandQueryRepository;
import com.loopers.catalog.brand.domain.repository.vo.PageCriteria;
import com.loopers.catalog.brand.domain.repository.vo.PageResult;
import com.loopers.catalog.brand.infrastructure.jpa.BrandJpaRepository;
import com.loopers.catalog.brand.infrastructure.mapper.BrandEntityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.Optional;


@Repository
@RequiredArgsConstructor
public class BrandQueryRepositoryImpl implements BrandQueryRepository {

	// jpa
	private final BrandJpaRepository brandJpaRepository;
	// mapper
	private final BrandEntityMapper brandMapper;


	/**
	 * 브랜드 조회 리포지토리 구현체
	 * 1. ID로 브랜드 조회
	 * 2. 브랜드 목록 조회 (페이지네이션)
	 * 3. ID로 노출 브랜드 조회 (VISIBLE + 미삭제)
	 * 4. 노출 브랜드 목록 조회 (VISIBLE만 페이지네이션)
	 * 5. 노출 상태별 브랜드 목록 조회 (특정 상태 필터)
	 */

	// 1. ID로 브랜드 조회
	@Override
	public Optional<Brand> findById(Long id) {
		return brandJpaRepository.findByIdAndDeletedAtIsNull(id)
			.map(brandMapper::toDomain);
	}


	// 2. 브랜드 목록 조회 (페이지네이션)
	@Override
	public PageResult<Brand> findAll(PageCriteria pageCriteria) {

		// 페이지 요청 생성
		var pageable = PageRequest.of(
			pageCriteria.page(),
			pageCriteria.size(),
			Sort.by(Sort.Direction.DESC, "id")
		);

		// 조회
		var page = brandJpaRepository.findAllByDeletedAtIsNull(pageable);

		// 도메인 변환
		return new PageResult<>(
			page.getContent().stream().map(brandMapper::toDomain).toList(),
			page.getNumber(),
			page.getSize(),
			page.getTotalElements()
		);
	}


	// 3. ID로 노출 브랜드 조회 (VISIBLE + 미삭제)
	@Override
	public Optional<Brand> findVisibleById(Long id) {
		return brandJpaRepository.findByIdAndVisibleStatusAndDeletedAtIsNull(id, VisibleStatus.VISIBLE)
			.map(brandMapper::toDomain);
	}


	// 4. 노출 브랜드 목록 조회 (VISIBLE만 페이지네이션)
	@Override
	public PageResult<Brand> findAllVisible(PageCriteria pageCriteria) {

		// 페이지 요청 생성
		var pageable = PageRequest.of(
			pageCriteria.page(),
			pageCriteria.size(),
			Sort.by(Sort.Direction.DESC, "id")
		);

		// 조회
		var page = brandJpaRepository.findAllByVisibleStatusAndDeletedAtIsNull(VisibleStatus.VISIBLE, pageable);

		// 도메인 변환
		return new PageResult<>(
			page.getContent().stream().map(brandMapper::toDomain).toList(),
			page.getNumber(),
			page.getSize(),
			page.getTotalElements()
		);
	}


	// 5. 노출 상태별 브랜드 목록 조회 (특정 상태 필터)
	@Override
	public PageResult<Brand> findAllByVisibleStatus(VisibleStatus visibleStatus, PageCriteria pageCriteria) {

		// 페이지 요청 생성
		var pageable = PageRequest.of(
			pageCriteria.page(),
			pageCriteria.size(),
			Sort.by(Sort.Direction.DESC, "id")
		);

		// 조회
		var page = brandJpaRepository.findAllByVisibleStatusAndDeletedAtIsNull(visibleStatus, pageable);

		// 도메인 변환
		return new PageResult<>(
			page.getContent().stream().map(brandMapper::toDomain).toList(),
			page.getNumber(),
			page.getSize(),
			page.getTotalElements()
		);
	}

}
