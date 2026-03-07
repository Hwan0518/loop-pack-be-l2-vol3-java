package com.loopers.coupon.issuedcoupon.infrastructure.repository;


import com.loopers.coupon.issuedcoupon.domain.model.IssuedCoupon;
import com.loopers.coupon.issuedcoupon.domain.repository.IssuedCouponQueryRepository;
import com.loopers.coupon.issuedcoupon.infrastructure.jpa.IssuedCouponJpaRepository;
import com.loopers.coupon.issuedcoupon.infrastructure.mapper.IssuedCouponEntityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;


@Repository
@RequiredArgsConstructor
public class IssuedCouponQueryRepositoryImpl implements IssuedCouponQueryRepository {

	// jpa
	private final IssuedCouponJpaRepository issuedCouponJpaRepository;
	// mapper
	private final IssuedCouponEntityMapper issuedCouponEntityMapper;


	/**
	 * 발급 쿠폰 조회 리포지토리 구현체
	 * 1. ID로 발급 쿠폰 조회
	 * 2. 사용자 ID와 쿠폰 템플릿 ID로 발급 존재 여부 조회
	 * 3. 사용자 ID로 발급 쿠폰 목록 조회
	 * 4. 쿠폰 템플릿 ID로 발급 쿠폰 목록 조회
	 * 5. ID로 발급 쿠폰 조회 (비관적 쓰기 락)
	 */

	// 1. ID로 발급 쿠폰 조회
	@Override
	public Optional<IssuedCoupon> findById(Long id) {
		return issuedCouponJpaRepository.findById(id)
			.map(issuedCouponEntityMapper::toDomain);
	}


	// 2. 사용자 ID와 쿠폰 템플릿 ID로 발급 존재 여부 조회
	@Override
	public boolean existsByUserIdAndCouponTemplateId(Long userId, Long couponTemplateId) {
		return issuedCouponJpaRepository.existsByCouponTemplateIdAndUserId(couponTemplateId, userId);
	}


	// 3. 사용자 ID로 발급 쿠폰 목록 조회 (최신순)
	@Override
	public List<IssuedCoupon> findAllByUserId(Long userId) {
		return issuedCouponJpaRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
			.map(issuedCouponEntityMapper::toDomain)
			.toList();
	}


	// 4. 쿠폰 템플릿 ID로 발급 쿠폰 목록 조회 (최신순)
	@Override
	public List<IssuedCoupon> findAllByCouponTemplateId(Long couponTemplateId) {
		return issuedCouponJpaRepository.findAllByCouponTemplateIdOrderByCreatedAtDesc(couponTemplateId).stream()
			.map(issuedCouponEntityMapper::toDomain)
			.toList();
	}


	// 5. ID로 발급 쿠폰 조회 (비관적 쓰기 락)
	@Override
	public Optional<IssuedCoupon> findByIdForUpdate(Long id) {
		return issuedCouponJpaRepository.findByIdForUpdate(id)
			.map(issuedCouponEntityMapper::toDomain);
	}

}
