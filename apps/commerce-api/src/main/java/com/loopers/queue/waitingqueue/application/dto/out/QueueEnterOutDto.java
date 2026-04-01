package com.loopers.queue.waitingqueue.application.dto.out;


/**
 * 대기열 진입 결과 DTO
 * - enterReceipt: 진입 증명 (stateless 서명, position 조회 시 "처리 중" 구분용)
 */
public record QueueEnterOutDto(
	String status,
	int retryAfterMs,
	String refreshedToken,
	String enterReceipt
) {

	// factory
	public static QueueEnterOutDto of(String status, int retryAfterMs, String refreshedToken, String enterReceipt) {
		return new QueueEnterOutDto(status, retryAfterMs, refreshedToken, enterReceipt);
	}
}
