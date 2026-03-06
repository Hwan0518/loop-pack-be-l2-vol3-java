package com.loopers.coupon.issuedcoupon.domain.repository;


import com.loopers.coupon.issuedcoupon.domain.model.IssuedCoupon;

import java.util.List;
import java.util.Optional;


/**
 * 발급된 쿠폰 조회 리포지토리 인터페이스
 */
public interface IssuedCouponQueryRepository {

	// 1. ID로 발급 쿠폰 조회
	Optional<IssuedCoupon> findById(Long id);

	// 2. 사용자 ID와 쿠폰 템플릿 ID로 발급 존재 여부 조회
	boolean existsByUserIdAndCouponTemplateId(Long userId, Long couponTemplateId);

	// 3. 사용자 ID로 발급 쿠폰 목록 조회
	List<IssuedCoupon> findAllByUserId(Long userId);

	// 4. couponTemplateId로 발급 쿠폰 목록 조회 (관리자용 페이지)
	List<IssuedCoupon> findAllByCouponTemplateId(Long couponTemplateId);

}
