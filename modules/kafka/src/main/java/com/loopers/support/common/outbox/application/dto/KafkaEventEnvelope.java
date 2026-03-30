package com.loopers.support.common.outbox.application.dto;


import java.time.LocalDateTime;


/**
 * Kafka 이벤트 공통 JSON 포맷 (Consumer가 파싱하는 최상위 구조)
 * - eventId: Outbox ID (멱등 키)
 * - eventType: 이벤트 타입 (PRODUCT_LIKED, ORDER_CREATED 등)
 * - aggregateType: 집합 타입 (ORDER, PRODUCT, COUPON)
 * - aggregateId: 집합 ID
 * - data: 실제 페이로드 JSON 문자열
 * - occurredAt: 이벤트 발생 시각
 */
public record KafkaEventEnvelope(
	String eventId,
	String eventType,
	String aggregateType,
	String aggregateId,
	String data,
	LocalDateTime occurredAt
) {
}
