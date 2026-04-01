package com.loopers.queue.waitingqueue.application.facade;


import com.loopers.queue.waitingqueue.application.service.EntryTokenCommandService;
import org.springframework.stereotype.Service;


/**
 * 입장 토큰 퍼사드 (Cross-BC 전용 — ACL에서 호출)
 * - 입장 토큰 검증/삭제
 */
@Service
public class EntryTokenCommandFacade {

	// service
	private final EntryTokenCommandService entryTokenCommandService;


	public EntryTokenCommandFacade(EntryTokenCommandService entryTokenCommandService) {
		this.entryTokenCommandService = entryTokenCommandService;
	}


	// 1. 입장 토큰 원자적 소비 (검증 + 삭제를 한 번에)
	public void consumeEntryToken(Long userId, String entryToken) {
		entryTokenCommandService.consumeEntryToken(userId, entryToken);
	}
}
