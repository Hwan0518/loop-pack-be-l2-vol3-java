package com.loopers.coupon.application.port.out;


import java.util.Optional;


/**
 * 쿠폰 발급 요청 상태 관리 포트 (application → infrastructure 계약)
 * - 발급 요청 조회, 발급 완료/거부 상태 업데이트
 */
public interface CouponIssueRequestPort {

	// 1. 발급 요청 조회 (requestId 기반)
	Optional<Long> findIdByRequestId(String requestId);

	// 2. 발급 완료 처리
	void markIssued(Long issueRequestId);

	// 3. 거부 처리
	void reject(Long issueRequestId, String reason);

}
