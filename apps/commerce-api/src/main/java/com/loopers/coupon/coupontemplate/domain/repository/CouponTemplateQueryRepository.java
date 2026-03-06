package com.loopers.coupon.coupontemplate.domain.repository;


import com.loopers.coupon.coupontemplate.domain.model.CouponTemplate;
import com.loopers.coupon.coupontemplate.domain.repository.vo.PageCriteria;
import com.loopers.coupon.coupontemplate.domain.repository.vo.PageResult;

import java.util.Optional;


/**
 * 쿠폰 템플릿 조회 리포지토리 인터페이스
 */
public interface CouponTemplateQueryRepository {

	// 1. ID로 쿠폰 템플릿 조회 (삭제된 것 포함)
	Optional<CouponTemplate> findById(Long id);

	// 2. 쿠폰 템플릿 목록 페이지 조회 (삭제되지 않은 것만)
	PageResult<CouponTemplate> findAll(PageCriteria criteria);

}
