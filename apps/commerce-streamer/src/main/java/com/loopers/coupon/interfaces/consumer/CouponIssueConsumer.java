package com.loopers.coupon.interfaces.consumer;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.confg.kafka.KafkaConfig;
import com.loopers.coupon.application.service.CouponIssueProcessorService;
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
				JsonNode envelope = objectMapper.readTree(record.value());
				String eventId = envelope.get("eventId").asText();
				JsonNode data = objectMapper.readTree(envelope.get("data").asText());

				String requestId = data.get("requestId").asText();
				Long userId = data.get("userId").asLong();
				Long couponTemplateId = data.get("couponTemplateId").asLong();

				couponIssueProcessorService.processIssueRequest(eventId, requestId, userId, couponTemplateId);
			}
		} catch (Exception e) {
			log.error("[CouponIssue] 배치 처리 실패", e);
			throw new RuntimeException(e);
		}

		ack.acknowledge();
	}

}
