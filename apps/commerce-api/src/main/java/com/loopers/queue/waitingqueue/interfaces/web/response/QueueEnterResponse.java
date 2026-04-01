package com.loopers.queue.waitingqueue.interfaces.web.response;


import com.loopers.queue.waitingqueue.application.dto.out.QueueEnterOutDto;


/**
 * 대기열 진입 응답
 */
public record QueueEnterResponse(
	String status,
	int retryAfterMs,
	String refreshedToken
) {

	// factory
	public static QueueEnterResponse from(QueueEnterOutDto outDto) {
		return new QueueEnterResponse(outDto.status(), outDto.retryAfterMs(), outDto.refreshedToken());
	}
}
