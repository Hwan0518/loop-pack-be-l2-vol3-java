package com.loopers.logging.application.dto;


/**
 * 이벤트 로그 데이터 DTO (application 계층)
 * - Consumer → Service → Port 전달용
 */
public record EventLogData(
	String eventId,
	String consumerGroup,
	String eventType,
	String aggregateType,
	String aggregateId,
	String payload
) {
}
