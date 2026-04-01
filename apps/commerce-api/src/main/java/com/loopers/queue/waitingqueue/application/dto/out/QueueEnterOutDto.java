package com.loopers.queue.waitingqueue.application.dto.out;


/**
 * 대기열 진입 결과 DTO
 */
public record QueueEnterOutDto(
	String status,
	int retryAfterMs,
	String refreshedToken
) {

	// factory
	public static QueueEnterOutDto of(String status, int retryAfterMs, String refreshedToken) {
		return new QueueEnterOutDto(status, retryAfterMs, refreshedToken);
	}
}
