package com.loopers.coupon.issuedcoupon.domain.repository;


import com.loopers.coupon.issuedcoupon.domain.model.CouponIssueRequest;


/**
 * 쿠폰 발급 요청 명령 레포지토리
 * 1. 저장
 */
public interface CouponIssueRequestCommandRepository {

	// 1. 저장
	CouponIssueRequest save(CouponIssueRequest request);

}
