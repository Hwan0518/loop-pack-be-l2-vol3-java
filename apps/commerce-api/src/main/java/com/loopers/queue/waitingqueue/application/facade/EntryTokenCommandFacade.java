package com.loopers.queue.waitingqueue.application.facade;


import com.loopers.queue.waitingqueue.application.service.EntryTokenCommandService;
import org.springframework.stereotype.Service;


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
	public String validateAndLock(Long userId, String entryToken) {
		return entryTokenCommandService.validateAndLock(userId, entryToken);
	}


	// 2. 주문 완료 후 정리 (토큰 삭제 + 락 해제, best-effort)
	public void completeOrder(Long userId, String lockValue) {
		entryTokenCommandService.completeOrder(userId, lockValue);
	}


	// 3. 주문 실패 후 정리 (락만 해제 — 토큰 보존)
	public void releaseOrderLock(Long userId, String lockValue) {
		entryTokenCommandService.releaseOrderLock(userId, lockValue);
	}
}
