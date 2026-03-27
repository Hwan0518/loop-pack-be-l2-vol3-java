package com.loopers.coupon.application.service;


import com.loopers.coupon.application.dto.CouponTemplateInfo;
import com.loopers.coupon.application.port.out.CouponIssueRequestPort;
import com.loopers.coupon.application.port.out.CouponTemplateReaderPort;
import com.loopers.coupon.application.port.out.IssuedCouponPort;
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

	// port
	private final IssuedCouponPort issuedCouponPort;
	private final CouponTemplateReaderPort couponTemplateReaderPort;
	private final CouponIssueRequestPort couponIssueRequestPort;
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
		Optional<Long> issueRequestIdOpt = couponIssueRequestPort.findIdByRequestId(requestId);
		if (issueRequestIdOpt.isEmpty()) {
			log.warn("[CouponIssue] 발급 요청 없음 requestId={}", requestId);
			eventIdempotencyService.markHandled(eventId, CONSUMER_GROUP);
			return;
		}
		Long issueRequestId = issueRequestIdOpt.get();

		// 템플릿 존재/삭제/만료 검증
		Optional<CouponTemplateInfo> templateOpt = couponTemplateReaderPort.findById(couponTemplateId);
		if (templateOpt.isEmpty()) {
			couponIssueRequestPort.reject(issueRequestId, "COUPON_TEMPLATE_NOT_FOUND");
			eventIdempotencyService.markHandled(eventId, CONSUMER_GROUP);
			log.warn("[CouponIssue] 템플릿 없음 requestId={} templateId={}", requestId, couponTemplateId);
			return;
		}
		CouponTemplateInfo template = templateOpt.get();
		if (template.deleted()) {
			couponIssueRequestPort.reject(issueRequestId, "COUPON_TEMPLATE_DELETED");
			eventIdempotencyService.markHandled(eventId, CONSUMER_GROUP);
			log.info("[CouponIssue] 삭제된 템플릿 거부 requestId={}", requestId);
			return;
		}
		if (template.expired()) {
			couponIssueRequestPort.reject(issueRequestId, "COUPON_EXPIRED");
			eventIdempotencyService.markHandled(eventId, CONSUMER_GROUP);
			log.info("[CouponIssue] 만료된 템플릿 거부 requestId={}", requestId);
			return;
		}

		// 중복 발급 검사
		if (issuedCouponPort.existsByUserIdAndTemplateId(userId, couponTemplateId)) {
			couponIssueRequestPort.reject(issueRequestId, "COUPON_ISSUE_DUPLICATED");
			eventIdempotencyService.markHandled(eventId, CONSUMER_GROUP);
			log.info("[CouponIssue] 중복 발급 거부 requestId={} userId={}", requestId, userId);
			return;
		}

		// 수량 확인
		if (template.maxQuantity() != null) {
			long issuedCount = issuedCouponPort.countByTemplateId(couponTemplateId);
			if (issuedCount >= template.maxQuantity()) {
				couponIssueRequestPort.reject(issueRequestId, "COUPON_SOLD_OUT");
				eventIdempotencyService.markHandled(eventId, CONSUMER_GROUP);
				log.info("[CouponIssue] 수량 초과 거부 requestId={} issued={} max={}",
					requestId, issuedCount, template.maxQuantity());
				return;
			}
		}

		// 발급
		issuedCouponPort.issue(userId, couponTemplateId);
		couponIssueRequestPort.markIssued(issueRequestId);

		// event_handled 기록
		eventIdempotencyService.markHandled(eventId, CONSUMER_GROUP);

		log.info("[CouponIssue] 발급 완료 requestId={} userId={} templateId={}", requestId, userId, couponTemplateId);
	}

}
