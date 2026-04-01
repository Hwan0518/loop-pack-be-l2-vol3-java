package com.loopers.queue.waitingqueue.application.service;


import com.loopers.queue.waitingqueue.application.port.out.WaitingQueueRedisPort;
import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import org.springframework.stereotype.Service;


/**
 * 입장 토큰 검증/삭제 서비스
 * - 주문 API 진입 시 토큰 검증
 * - 주문 완료 후 토큰 삭제
 */
@Service
public class EntryTokenCommandService {

	// port
	private final WaitingQueueRedisPort waitingQueueRedisPort;


	public EntryTokenCommandService(WaitingQueueRedisPort waitingQueueRedisPort) {
		this.waitingQueueRedisPort = waitingQueueRedisPort;
	}


	// 1. 입장 토큰 검증 (읽기 전용 — 순번 조회용)
	public void validateEntryToken(Long userId, String entryToken) {
		String storedToken = waitingQueueRedisPort.getEntryToken(userId);

		if (storedToken == null || !storedToken.equals(entryToken)) {
			throw new CoreException(ErrorType.INVALID_QUEUE_TOKEN);
		}
	}


	// 2. 입장 토큰 원자적 소비 (GETDEL — 검증 + 삭제를 한 번에, 동시 주문 방지)
	public void consumeEntryToken(Long userId, String entryToken) {
		String storedToken = waitingQueueRedisPort.consumeEntryToken(userId);

		if (storedToken == null || !storedToken.equals(entryToken)) {
			throw new CoreException(ErrorType.INVALID_QUEUE_TOKEN);
		}
	}


	// 3. 입장 토큰 삭제 (주문 완료 후)
	public void deleteEntryToken(Long userId) {
		waitingQueueRedisPort.deleteEntryToken(userId);
	}
}
