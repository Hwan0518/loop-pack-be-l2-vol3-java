package com.loopers.queue.waitingqueue.interfaces.web.response;


import com.loopers.queue.waitingqueue.application.dto.out.QueuePositionOutDto;


/**
 * 순번 조회 응답
 */
public record QueuePositionResponse(
	long position,
	long totalWaiting,
	double estimatedWaitSeconds,
	int retryAfterMs,
	String refreshedToken,
	String entryToken
) {

	// factory
	public static QueuePositionResponse from(QueuePositionOutDto outDto) {
		return new QueuePositionResponse(
			outDto.position(),
			outDto.totalWaiting(),
			outDto.estimatedWaitSeconds(),
			outDto.retryAfterMs(),
			outDto.refreshedToken(),
			outDto.entryToken()
		);
	}
}
