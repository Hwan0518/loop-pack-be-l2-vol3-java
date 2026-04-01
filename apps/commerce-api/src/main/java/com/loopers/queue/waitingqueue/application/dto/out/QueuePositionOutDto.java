package com.loopers.queue.waitingqueue.application.dto.out;


/**
 * 순번 조회 결과 DTO
 * - position: 1-based 순번 (0이면 입장 토큰 발급 완료)
 * - totalWaiting: 전체 대기 인원
 * - estimatedWaitSeconds: 예상 대기 시간
 * - retryAfterMs: 다음 Polling까지 대기 시간
 * - refreshedToken: 갱신된 서명 토큰
 * - entryToken: 입장 토큰 (position=0일 때만)
 */
public record QueuePositionOutDto(
	long position,
	long totalWaiting,
	double estimatedWaitSeconds,
	int retryAfterMs,
	String refreshedToken,
	String entryToken
) {

	// factory — 대기 중
	public static QueuePositionOutDto waiting(long position, long totalWaiting,
		double estimatedWaitSeconds, int retryAfterMs, String refreshedToken) {
		return new QueuePositionOutDto(position, totalWaiting, estimatedWaitSeconds, retryAfterMs, refreshedToken, null);
	}

	// factory — 입장 토큰 발급 완료
	public static QueuePositionOutDto tokenIssued(String entryToken, String refreshedToken) {
		return new QueuePositionOutDto(0, 0, 0, 0, refreshedToken, entryToken);
	}
}
