package com.loopers.queue.waitingqueue.application.dto.out;


/**
 * 대기열 상태
 * - WAITING: 대기 중
 * - TOKEN_ISSUED: 입장 토큰 발급 완료
 * - PROCESSING: Consumer 처리 대기 중 (진입 증명 확인됨)
 * - ACCEPTED: 대기열 진입 수락
 */
public enum QueueStatus {
	WAITING, TOKEN_ISSUED, PROCESSING, ACCEPTED
}
