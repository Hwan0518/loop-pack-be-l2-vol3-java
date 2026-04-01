package com.loopers.queue.waitingqueue.application.facade;


import com.loopers.queue.waitingqueue.application.service.EntryTokenCommandService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
public class EntryTokenCommandFacade {

	// service
	private final EntryTokenCommandService entryTokenCommandService;

	/**
	 * 입장 토큰 퍼사드 (Cross-BC 전용 — ACL에서 호출)
	 * 1. 입장 토큰 검증 + 주문 처리 락 획득
	 * 2. 주문 완료 후 정리
	 * 3. 주문 실패 후 정리
	 */

	public EntryTokenCommandFacade(EntryTokenCommandService entryTokenCommandService) {
		this.entryTokenCommandService = entryTokenCommandService;
	}


	// 1. 입장 토큰 검증 + 주문 처리 락 획득 (동시 주문 방지)
	@Transactional
	public void validateAndLock(Long userId, String entryToken) {
		entryTokenCommandService.validateAndLock(userId, entryToken);
	}


	// 2. 주문 완료 후 정리 (토큰 삭제 + 락 해제, best-effort)
	@Transactional
	public void completeOrder(Long userId) {
		entryTokenCommandService.completeOrder(userId);
	}


	// 3. 주문 실패 후 정리 (락만 해제 — 토큰 보존)
	@Transactional
	public void releaseOrderLock(Long userId) {
		entryTokenCommandService.releaseOrderLock(userId);
	}
}
