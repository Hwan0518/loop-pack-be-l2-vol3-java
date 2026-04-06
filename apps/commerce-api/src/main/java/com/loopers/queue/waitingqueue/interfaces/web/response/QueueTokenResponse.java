package com.loopers.queue.waitingqueue.interfaces.web.response;


import com.loopers.queue.waitingqueue.application.dto.out.QueueTokenOutDto;


/**
 * 대기열 토큰 발급 응답
 */
public record QueueTokenResponse(
	String queueToken
) {

	// factory
	public static QueueTokenResponse from(QueueTokenOutDto outDto) {
		return new QueueTokenResponse(outDto.queueToken());
	}
}
