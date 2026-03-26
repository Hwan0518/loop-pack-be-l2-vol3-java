package com.loopers.coupon.issuedcoupon.domain.repository;


import com.loopers.coupon.issuedcoupon.domain.model.CouponIssueRequest;

import java.util.Optional;


/**
 * 쿠폰 발급 요청 조회 레포지토리
 * 1. requestId로 조회
 * 2. requestId + userId로 조회
 */
public interface CouponIssueRequestQueryRepository {

	// 1. requestId로 조회
	Optional<CouponIssueRequest> findByRequestId(String requestId);

	// 2. requestId + userId로 조회
	Optional<CouponIssueRequest> findByRequestIdAndUserId(String requestId, Long userId);

}
