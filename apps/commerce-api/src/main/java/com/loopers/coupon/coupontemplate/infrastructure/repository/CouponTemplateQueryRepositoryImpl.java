package com.loopers.coupon.coupontemplate.infrastructure.repository;


import com.loopers.coupon.coupontemplate.domain.model.CouponTemplate;
import com.loopers.coupon.coupontemplate.domain.repository.CouponTemplateQueryRepository;
import com.loopers.coupon.coupontemplate.domain.repository.vo.PageCriteria;
import com.loopers.coupon.coupontemplate.domain.repository.vo.PageResult;
import com.loopers.coupon.coupontemplate.infrastructure.entity.CouponTemplateEntity;
import com.loopers.coupon.coupontemplate.infrastructure.jpa.CouponTemplateJpaRepository;
import com.loopers.coupon.coupontemplate.infrastructure.mapper.CouponTemplateEntityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.Optional;


@Repository
@RequiredArgsConstructor
public class CouponTemplateQueryRepositoryImpl implements CouponTemplateQueryRepository {

	// jpa
	private final CouponTemplateJpaRepository couponTemplateJpaRepository;
	// mapper
	private final CouponTemplateEntityMapper couponTemplateEntityMapper;


	/**
	 * 쿠폰 템플릿 조회 리포지토리 구현체
	 * 1. ID로 쿠폰 템플릿 조회 (삭제된 것 포함)
	 * 2. 쿠폰 템플릿 목록 페이지 조회 (삭제되지 않은 것만)
	 */

	// 1. ID로 쿠폰 템플릿 조회 (삭제된 것 포함)
	@Override
	public Optional<CouponTemplate> findById(Long id) {
		return couponTemplateJpaRepository.findById(id)
			.map(couponTemplateEntityMapper::toDomain);
	}


	// 2. 쿠폰 템플릿 목록 페이지 조회 (삭제되지 않은 것만)
	@Override
	public PageResult<CouponTemplate> findAll(PageCriteria criteria) {

		// Pageable 생성 (id 내림차순)
		PageRequest pageable = PageRequest.of(criteria.page(), criteria.size(), Sort.by(Sort.Direction.DESC, "id"));

		// 삭제되지 않은 쿠폰 템플릿 목록 조회
		Page<CouponTemplateEntity> page = couponTemplateJpaRepository.findAllByDeletedAtIsNull(pageable);

		// PageResult 변환
		return new PageResult<>(
			page.getContent().stream().map(couponTemplateEntityMapper::toDomain).toList(),
			page.getNumber(),
			page.getSize(),
			page.getTotalElements()
		);
	}

}
