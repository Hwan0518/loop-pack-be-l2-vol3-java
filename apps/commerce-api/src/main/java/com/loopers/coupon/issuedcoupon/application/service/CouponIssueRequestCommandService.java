package com.loopers.coupon.issuedcoupon.application.service;


import com.loopers.coupon.issuedcoupon.application.dto.out.CouponIssueRequestOutDto;
import com.loopers.support.common.event.coupon.CouponIssueRequestedPayload;
import com.loopers.coupon.issuedcoupon.domain.model.CouponIssueRequest;
import com.loopers.coupon.issuedcoupon.domain.repository.CouponIssueRequestCommandRepository;
import com.loopers.coupon.issuedcoupon.domain.repository.CouponIssueRequestQueryRepository;
import com.loopers.support.common.event.EventType;
import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import com.loopers.support.common.outbox.application.port.OutboxEventPort;
import com.loopers.support.common.outbox.application.util.JsonSerializer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;


/**
 * 쿠폰 발급 요청 명령 서비스
 * 1. 발급 요청 생성 (PENDING + Outbox 저장)
 * 2. 발급 요청 조회 (polling용)
 */

@Service
@RequiredArgsConstructor
public class CouponIssueRequestCommandService {

	// repository
	private final CouponIssueRequestCommandRepository couponIssueRequestCommandRepository;
	private final CouponIssueRequestQueryRepository couponIssueRequestQueryRepository;
	// outbox
	private final OutboxEventPort outboxEventPort;
	private final JsonSerializer jsonSerializer;


	// 1. 발급 요청 생성 (멱등: requestId 기반 중복 검사)
	@Transactional
	public CouponIssueRequestOutDto createIssueRequest(Long userId, Long couponTemplateId, String requestId) {

		// requestId 미전달 시 서버 생성
		String resolvedRequestId = (requestId != null && !requestId.isBlank()) ? requestId : UUID.randomUUID().toString();

		// 멱등 검사: 기존 요청 존재 시 기존 반환 (동일 userId만 허용 — 타인의 requestId 접근 방지)
		Optional<CouponIssueRequest> existing = couponIssueRequestQueryRepository.findByRequestId(resolvedRequestId);
		if (existing.isPresent()) {
			CouponIssueRequest request = existing.get();
			if (!request.getUserId().equals(userId)) {
				throw new CoreException(ErrorType.COUPON_ISSUE_REQUEST_NOT_FOUND);
			}
			return new CouponIssueRequestOutDto(request.getRequestId(), request.getStatus().name());
		}

		// 발급 요청 저장 (PENDING)
		CouponIssueRequest request = CouponIssueRequest.create(resolvedRequestId, userId, couponTemplateId);
		couponIssueRequestCommandRepository.save(request);

		// Outbox 저장 (같은 TX — coupon-issue-requests 토픽)
		outboxEventPort.save(EventType.COUPON_ISSUE_REQUESTED, String.valueOf(couponTemplateId),
			String.valueOf(couponTemplateId),
			jsonSerializer.toJson(CouponIssueRequestedPayload.of(resolvedRequestId, userId, couponTemplateId)));

		return new CouponIssueRequestOutDto(resolvedRequestId, "PENDING");
	}


	// 2. 발급 요청 조회 (polling용 — userId 검증)
	@Transactional(readOnly = true)
	public CouponIssueRequestOutDto getIssueRequest(String requestId, Long userId) {

		CouponIssueRequest request = couponIssueRequestQueryRepository.findByRequestIdAndUserId(requestId, userId)
			.orElseThrow(() -> new CoreException(ErrorType.COUPON_ISSUE_REQUEST_NOT_FOUND));

		return new CouponIssueRequestOutDto(request.getRequestId(), request.getStatus().name());
	}

}
