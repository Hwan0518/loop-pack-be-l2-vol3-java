package com.loopers.coupon.issuedcoupon.domain.model;


import com.loopers.coupon.issuedcoupon.domain.model.enums.CouponIssueRequestStatus;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Objects;


@Getter
public class CouponIssueRequest {

	/**
	 * 쿠폰 발급 요청
	 * - id: 발급 요청 ID
	 * - requestId: 멱등성 보장용 요청 ID
	 * - userId: 요청 사용자 ID
	 * - couponTemplateId: 쿠폰 템플릿 ID
	 * - status: 상태 (PENDING → ISSUED / REJECTED)
	 * - rejectReason: 거부 사유 (REJECTED 시)
	 * - createdAt: 생성 일시
	 */

	private final Long id;
	private final String requestId;
	private final Long userId;
	private final Long couponTemplateId;
	private CouponIssueRequestStatus status;
	private String rejectReason;
	private final LocalDateTime createdAt;


	// 생성자
	private CouponIssueRequest(Long id, String requestId, Long userId, Long couponTemplateId,
		CouponIssueRequestStatus status, String rejectReason, LocalDateTime createdAt) {
		this.id = id;
		this.requestId = requestId;
		this.userId = userId;
		this.couponTemplateId = couponTemplateId;
		this.status = status;
		this.rejectReason = rejectReason;
		this.createdAt = createdAt;
	}


	/**
	 * 도메인 로직
	 * 1. 발급 요청 생성
	 * 2. 재생성 (Entity -> Model 매핑용도)
	 * 3. 발급 완료 처리
	 * 4. 거부 처리
	 */

	// 1. 발급 요청 생성 (status = PENDING)
	public static CouponIssueRequest create(String requestId, Long userId, Long couponTemplateId) {
		Objects.requireNonNull(requestId, "requestId");
		Objects.requireNonNull(userId, "userId");
		Objects.requireNonNull(couponTemplateId, "couponTemplateId");
		return new CouponIssueRequest(null, requestId, userId, couponTemplateId,
			CouponIssueRequestStatus.PENDING, null, null);
	}


	// 2. 재생성 (Entity -> Model 매핑용도)
	public static CouponIssueRequest reconstruct(Long id, String requestId, Long userId, Long couponTemplateId,
		CouponIssueRequestStatus status, String rejectReason, LocalDateTime createdAt) {
		return new CouponIssueRequest(id, requestId, userId, couponTemplateId, status, rejectReason, createdAt);
	}


	// 3. 발급 완료 처리
	public void markIssued() {
		this.status = CouponIssueRequestStatus.ISSUED;
	}


	// 4. 거부 처리
	public void reject(String reason) {
		this.status = CouponIssueRequestStatus.REJECTED;
		this.rejectReason = reason;
	}

}
