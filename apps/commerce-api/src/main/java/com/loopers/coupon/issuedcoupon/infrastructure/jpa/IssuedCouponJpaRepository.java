package com.loopers.coupon.issuedcoupon.infrastructure.jpa;


import com.loopers.coupon.issuedcoupon.infrastructure.entity.IssuedCouponEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;


/**
 * 발급된 쿠폰 JPA 리포지토리
 */
public interface IssuedCouponJpaRepository extends JpaRepository<IssuedCouponEntity, Long> {

	// 사용자 ID와 쿠폰 템플릿 ID로 발급 존재 여부 조회
	boolean existsByCouponTemplateIdAndUserId(Long couponTemplateId, Long userId);

	// 사용자 ID로 발급 쿠폰 목록 조회 (최신순)
	List<IssuedCouponEntity> findAllByUserIdOrderByCreatedAtDesc(Long userId);

	// 쿠폰 템플릿 ID로 발급 쿠폰 목록 조회 (최신순)
	List<IssuedCouponEntity> findAllByCouponTemplateIdOrderByCreatedAtDesc(Long couponTemplateId);

}
