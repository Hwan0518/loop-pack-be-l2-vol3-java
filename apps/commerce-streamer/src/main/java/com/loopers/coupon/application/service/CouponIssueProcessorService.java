package com.loopers.coupon.application.service;


import com.loopers.coupon.infrastructure.entity.StreamerCouponIssueRequestEntity;
import com.loopers.coupon.infrastructure.entity.StreamerCouponTemplateEntity;
import com.loopers.coupon.infrastructure.entity.StreamerIssuedCouponEntity;
import com.loopers.coupon.infrastructure.jpa.StreamerCouponIssueRequestJpaRepository;
import com.loopers.coupon.infrastructure.jpa.StreamerCouponTemplateJpaRepository;
import com.loopers.coupon.infrastructure.jpa.StreamerIssuedCouponJpaRepository;
import com.loopers.support.idempotency.EventIdempotencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;


/**
 * 쿠폰 발급 처리 서비스 (commerce-streamer — CouponIssueConsumer에서 호출)
 * 1. 발급 요청 처리 (멱등 + 중복 발급 + 수량 확인 + 발급)
 */

@Service
@RequiredArgsConstructor
@Slf4j
public class CouponIssueProcessorService {

	private static final String CONSUMER_GROUP = "coupon-issuer";

	// jpa
	private final StreamerIssuedCouponJpaRepository issuedCouponJpaRepository;
	private final StreamerCouponTemplateJpaRepository couponTemplateJpaRepository;
	private final StreamerCouponIssueRequestJpaRepository couponIssueRequestJpaRepository;
	// idempotency
	private final EventIdempotencyService eventIdempotencyService;


	// 1. 발급 요청 처리
	@Transactional
	public void processIssueRequest(String eventId, String requestId, Long userId, Long couponTemplateId) {

		// 멱등 검사
		if (eventIdempotencyService.isAlreadyHandled(eventId, CONSUMER_GROUP)) {
			return;
		}

		// 발급 요청 조회
		Optional<StreamerCouponIssueRequestEntity> requestOpt =
			couponIssueRequestJpaRepository.findByRequestId(requestId);
		if (requestOpt.isEmpty()) {
			log.warn("[CouponIssue] 발급 요청 없음 requestId={}", requestId);
			eventIdempotencyService.markHandled(eventId, CONSUMER_GROUP);
			return;
		}
		StreamerCouponIssueRequestEntity issueRequest = requestOpt.get();

		// 중복 발급 검사
		if (issuedCouponJpaRepository.existsByUserIdAndCouponTemplateId(userId, couponTemplateId)) {
			issueRequest.reject("COUPON_ISSUE_DUPLICATED");
			eventIdempotencyService.markHandled(eventId, CONSUMER_GROUP);
			log.info("[CouponIssue] 중복 발급 거부 requestId={} userId={}", requestId, userId);
			return;
		}

		// 수량 확인
		Optional<StreamerCouponTemplateEntity> templateOpt = couponTemplateJpaRepository.findById(couponTemplateId);
		if (templateOpt.isPresent() && templateOpt.get().getMaxQuantity() != null) {
			long issuedCount = issuedCouponJpaRepository.countByCouponTemplateId(couponTemplateId);
			if (issuedCount >= templateOpt.get().getMaxQuantity()) {
				issueRequest.reject("COUPON_SOLD_OUT");
				eventIdempotencyService.markHandled(eventId, CONSUMER_GROUP);
				log.info("[CouponIssue] 수량 초과 거부 requestId={} issued={} max={}",
					requestId, issuedCount, templateOpt.get().getMaxQuantity());
				return;
			}
		}

		// 발급
		issuedCouponJpaRepository.save(StreamerIssuedCouponEntity.of(userId, couponTemplateId));
		issueRequest.markIssued();

		// event_handled 기록
		eventIdempotencyService.markHandled(eventId, CONSUMER_GROUP);

		log.info("[CouponIssue] 발급 완료 requestId={} userId={} templateId={}", requestId, userId, couponTemplateId);
	}

}
