package com.loopers.coupon.interfaces.consumer;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.coupon.application.service.CouponIssueProcessorService;
import com.loopers.support.common.event.coupon.CouponIssueRequestedPayload;
import com.loopers.support.common.outbox.application.dto.KafkaEventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.List;


/**
 * 쿠폰 발급 Consumer (consumer group: coupon-issuer)
 * - coupon-issue-requests 토픽 구독
 * - 단일 파티션 순차 처리로 동시성 제어
 */

@Component
@RequiredArgsConstructor
@Slf4j
public class CouponIssueConsumer {

	// service
	private final CouponIssueProcessorService couponIssueProcessorService;
	// json
	private final ObjectMapper objectMapper;


	@KafkaListener(
		topics = "coupon-issue-requests",
		groupId = "coupon-issuer",
		containerFactory = KafkaConfig.SINGLE_LISTENER_COUPON
	)
	public void consume(List<ConsumerRecord<String, byte[]>> records, Acknowledgment ack) {
		try {
			for (ConsumerRecord<String, byte[]> record : records) {
				KafkaEventEnvelope envelope = objectMapper.readValue(record.value(), KafkaEventEnvelope.class);
				CouponIssueRequestedPayload payload = objectMapper.readValue(
					envelope.data(), CouponIssueRequestedPayload.class);

				couponIssueProcessorService.processIssueRequest(
					envelope.eventId(), payload.requestId(), payload.userId(), payload.couponTemplateId());
			}
		} catch (Exception e) {
			log.error("[CouponIssue] 배치 처리 실패", e);
			throw new RuntimeException(e);
		}

		ack.acknowledge();
	}

}
