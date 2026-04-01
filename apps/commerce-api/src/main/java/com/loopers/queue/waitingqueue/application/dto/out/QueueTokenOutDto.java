package com.loopers.queue.waitingqueue.application.dto.out;


/**
 * 대기열 토큰 발급 결과 DTO
 */
public record QueueTokenOutDto(
	String queueToken
) {

	// factory
	public static QueueTokenOutDto of(String queueToken) {
		return new QueueTokenOutDto(queueToken);
	}
}
