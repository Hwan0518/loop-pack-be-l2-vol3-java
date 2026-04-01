package com.loopers.queue.waitingqueue.interfaces.web.response;


import com.loopers.queue.waitingqueue.application.dto.out.QueueEnterOutDto;


/**
 * 대기열 진입 응답
 * - enterReceipt: 클라이언트가 저장 후 순번 조회 시 X-Enter-Receipt 헤더로 전달
 */
public record QueueEnterResponse(
	String status,
	int retryAfterMs,
	String refreshedToken,
	String enterReceipt
) {

	// factory
	public static QueueEnterResponse from(QueueEnterOutDto outDto) {
		return new QueueEnterResponse(outDto.status(), outDto.retryAfterMs(), outDto.refreshedToken(), outDto.enterReceipt());
	}
}
