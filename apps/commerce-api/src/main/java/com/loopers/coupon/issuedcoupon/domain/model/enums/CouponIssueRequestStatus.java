package com.loopers.coupon.issuedcoupon.domain.model.enums;


/**
 * 쿠폰 발급 요청 상태
 * - PENDING: 대기 (Kafka 처리 전)
 * - ISSUED: 발급 완료
 * - REJECTED: 발급 거부 (수량 초과, 중복 등)
 */
public enum CouponIssueRequestStatus {

	PENDING,
	ISSUED,
	REJECTED

}
