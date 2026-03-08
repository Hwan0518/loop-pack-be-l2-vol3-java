package com.loopers.coupon.issuedcoupon.application.service;


import com.loopers.coupon.issuedcoupon.domain.model.IssuedCoupon;
import com.loopers.coupon.issuedcoupon.domain.repository.IssuedCouponQueryRepository;
import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


@Service
@RequiredArgsConstructor
public class IssuedCouponQueryService {

	// repository
	private final IssuedCouponQueryRepository issuedCouponQueryRepository;


	/**
	 * 발급 쿠폰 조회 서비스
	 * 1. ID로 발급 쿠폰 조회 (없으면 예외)
	 * 2. 사용자 ID로 발급 쿠폰 목록 조회
	 * 3. 쿠폰 템플릿 ID로 발급 쿠폰 목록 조회
	 */

	// 1. ID로 발급 쿠폰 조회 (없으면 예외)
	@Transactional(readOnly = true)
	public IssuedCoupon getById(Long id) {
		return issuedCouponQueryRepository.findById(id)
			.orElseThrow(() -> new CoreException(ErrorType.ISSUED_COUPON_NOT_FOUND));
	}


	// 2. 사용자 ID로 발급 쿠폰 목록 조회
	@Transactional(readOnly = true)
	public List<IssuedCoupon> getAllByUserId(Long userId) {
		return issuedCouponQueryRepository.findAllByUserId(userId);
	}


	// 3. 쿠폰 템플릿 ID로 발급 쿠폰 목록 조회 (관리자용)
	@Transactional(readOnly = true)
	public List<IssuedCoupon> getAllByCouponTemplateId(Long couponTemplateId) {
		return issuedCouponQueryRepository.findAllByCouponTemplateId(couponTemplateId);
	}

}
