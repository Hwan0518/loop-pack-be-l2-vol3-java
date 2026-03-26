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

		// 템플릿 존재/삭제/만료 검증
		Optional<StreamerCouponTemplateEntity> templateOpt = couponTemplateJpaRepository.findById(couponTemplateId);
		if (templateOpt.isEmpty()) {
			issueRequest.reject("COUPON_TEMPLATE_NOT_FOUND");
			eventIdempotencyService.markHandled(eventId, CONSUMER_GROUP);
			log.warn("[CouponIssue] 템플릿 없음 requestId={} templateId={}", requestId, couponTemplateId);
			return;
		}
		StreamerCouponTemplateEntity template = templateOpt.get();
		if (template.isDeleted()) {
			issueRequest.reject("COUPON_TEMPLATE_DELETED");
			eventIdempotencyService.markHandled(eventId, CONSUMER_GROUP);
			log.info("[CouponIssue] 삭제된 템플릿 거부 requestId={}", requestId);
			return;
		}
		if (template.isExpired()) {
			issueRequest.reject("COUPON_EXPIRED");
			eventIdempotencyService.markHandled(eventId, CONSUMER_GROUP);
			log.info("[CouponIssue] 만료된 템플릿 거부 requestId={}", requestId);
			return;
		}

		// 중복 발급 검사
		if (issuedCouponJpaRepository.existsByUserIdAndCouponTemplateId(userId, couponTemplateId)) {
			issueRequest.reject("COUPON_ISSUE_DUPLICATED");
			eventIdempotencyService.markHandled(eventId, CONSUMER_GROUP);
			log.info("[CouponIssue] 중복 발급 거부 requestId={} userId={}", requestId, userId);
			return;
		}

		// 수량 확인
		if (template.getMaxQuantity() != null) {
			long issuedCount = issuedCouponJpaRepository.countByCouponTemplateId(couponTemplateId);
			if (issuedCount >= template.getMaxQuantity()) {
				issueRequest.reject("COUPON_SOLD_OUT");
				eventIdempotencyService.markHandled(eventId, CONSUMER_GROUP);
				log.info("[CouponIssue] 수량 초과 거부 requestId={} issued={} max={}",
					requestId, issuedCount, template.getMaxQuantity());
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
