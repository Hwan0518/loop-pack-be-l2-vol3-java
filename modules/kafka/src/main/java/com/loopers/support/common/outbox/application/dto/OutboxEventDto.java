package com.loopers.support.common.outbox.application.dto;


import java.time.LocalDateTime;


/**
 * Outbox 이벤트 조회 결과 DTO (app layer에서 다루는 데이터)
 */
public record OutboxEventDto(
	Long id,
	String aggregateType,
	String aggregateId,
	String eventType,
	String topic,
	String partitionKey,
	String payload,
	String status,
	int retryCount,
	LocalDateTime createdAt
) {
}
