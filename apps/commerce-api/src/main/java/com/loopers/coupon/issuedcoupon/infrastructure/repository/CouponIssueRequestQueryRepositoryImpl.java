package com.loopers.coupon.issuedcoupon.infrastructure.repository;


import com.loopers.coupon.issuedcoupon.domain.model.CouponIssueRequest;
import com.loopers.coupon.issuedcoupon.domain.repository.CouponIssueRequestQueryRepository;
import com.loopers.coupon.issuedcoupon.infrastructure.jpa.CouponIssueRequestJpaRepository;
import com.loopers.coupon.issuedcoupon.infrastructure.mapper.CouponIssueRequestEntityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;


/**
 * 쿠폰 발급 요청 조회 레포지토리 구현체
 * 1. requestId로 조회
 * 2. requestId + userId로 조회
 */

@Repository
@RequiredArgsConstructor
public class CouponIssueRequestQueryRepositoryImpl implements CouponIssueRequestQueryRepository {

	// jpa
	private final CouponIssueRequestJpaRepository jpaRepository;
	// mapper
	private final CouponIssueRequestEntityMapper mapper;


	// 1. requestId로 조회
	@Override
	public Optional<CouponIssueRequest> findByRequestId(String requestId) {
		return jpaRepository.findByRequestId(requestId).map(mapper::toDomain);
	}


	// 2. requestId + userId로 조회
	@Override
	public Optional<CouponIssueRequest> findByRequestIdAndUserId(String requestId, Long userId) {
		return jpaRepository.findByRequestIdAndUserId(requestId, userId).map(mapper::toDomain);
	}

}
