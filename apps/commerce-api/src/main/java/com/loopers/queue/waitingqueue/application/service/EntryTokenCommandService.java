package com.loopers.queue.waitingqueue.application.service;


import com.loopers.queue.waitingqueue.application.port.out.WaitingQueueRedisPort;
import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
public class EntryTokenCommandService {

	private static final long ORDER_LOCK_TTL_SECONDS = 300;
	private static final Logger log = LoggerFactory.getLogger(EntryTokenCommandService.class);

	// port
	private final WaitingQueueRedisPort waitingQueueRedisPort;

	/**
	 * 입장 토큰 검증/삭제 서비스
	 * 1. 입장 토큰 검증
	 * 2. 입장 토큰 검증 + 주문 처리 락 획득
	 * 3. 주문 완료 후 정리
	 * 4. 주문 실패 후 정리
	 */

	public EntryTokenCommandService(WaitingQueueRedisPort waitingQueueRedisPort) {
		this.waitingQueueRedisPort = waitingQueueRedisPort;
	}


	// 1. 입장 토큰 검증 (읽기 전용)
	@Transactional(readOnly = true)
	public void validateEntryToken(Long userId, String entryToken) {
		String storedToken = waitingQueueRedisPort.getEntryToken(userId);

		if (storedToken == null || !storedToken.equals(entryToken)) {
			throw new CoreException(ErrorType.INVALID_QUEUE_TOKEN);
		}
	}


	// 2. 입장 토큰 검증 + 주문 처리 락 획득 (동시 주문 방지)
	@Transactional
	public void validateAndLock(Long userId, String entryToken) {
		validateEntryToken(userId, entryToken);

		boolean acquired = waitingQueueRedisPort.acquireOrderLock(userId, ORDER_LOCK_TTL_SECONDS);
		if (!acquired) {
			throw new CoreException(ErrorType.ORDER_ALREADY_IN_PROGRESS);
		}
	}


	// 3. 주문 완료 후 정리 (토큰 삭제 성공 시에만 락 해제 — best-effort)
	@Transactional
	public void completeOrder(Long userId) {
		try {
			waitingQueueRedisPort.deleteEntryToken(userId);
		} catch (Exception e) {
			// 토큰 삭제가 실패하면 락을 유지해 entry-token 재사용을 TTL까지 막는다.
			log.warn("Failed to delete entry token after order completion. userId={}", userId, e);
			return;
		}

		try {
			waitingQueueRedisPort.releaseOrderLock(userId);
		} catch (Exception e) {
			log.warn("Failed to release order lock after entry token deletion. userId={}", userId, e);
		}
	}


	// 4. 주문 실패 후 정리 (락만 해제 — 토큰 보존하여 재시도 가능)
	@Transactional
	public void releaseOrderLock(Long userId) {
		waitingQueueRedisPort.releaseOrderLock(userId);
	}

}
