package com.loopers.coupon.infrastructure;


import com.loopers.coupon.application.port.out.CouponIssueRequestPort;
import com.loopers.coupon.infrastructure.entity.StreamerCouponIssueRequestEntity;
import com.loopers.coupon.infrastructure.jpa.StreamerCouponIssueRequestJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;


/**
 * 쿠폰 발급 요청 상태 관리 포트 구현체
 * - StreamerCouponIssueRequestJpaRepository 위임
 */

@Repository
@RequiredArgsConstructor
public class CouponIssueRequestPortImpl implements CouponIssueRequestPort {

	// jpa
	private final StreamerCouponIssueRequestJpaRepository couponIssueRequestJpaRepository;


	// 1. 발급 요청 조회 (requestId → entity ID 반환)
	@Override
	public Optional<Long> findIdByRequestId(String requestId) {
		return couponIssueRequestJpaRepository.findByRequestId(requestId)
			.map(StreamerCouponIssueRequestEntity::getId);
	}

	// 2. 발급 완료 처리
	@Override
	public void markIssued(Long issueRequestId) {
		StreamerCouponIssueRequestEntity entity = couponIssueRequestJpaRepository
			.findById(issueRequestId).orElseThrow();
		entity.markIssued();
	}

	// 3. 거부 처리
	@Override
	public void reject(Long issueRequestId, String reason) {
		StreamerCouponIssueRequestEntity entity = couponIssueRequestJpaRepository
			.findById(issueRequestId).orElseThrow();
		entity.reject(reason);
	}

}
