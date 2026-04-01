package com.loopers.ordering.order.application.port.out.client.queue;


/**
 * 주문 시 입장 토큰 검증/락 포트 (Cross-BC: ordering → queue)
 * - 검증 + 락: 토큰 검증 후 주문 처리 락 획득 (동시 주문 방지)
 * - 완료: 주문 성공 후 토큰 삭제 + 락 해제 (best-effort)
 * - 실패: 주문 실패 후 락만 해제 (토큰 보존 → 재시도 가능)
 */
public interface OrderEntryTokenValidator {

	// 1. 입장 토큰 검증 + 주문 처리 락 획득 (동시 주문 방지)
	void validateAndLock(Long userId, String entryToken);

	// 2. 주문 완료 후 정리 (토큰 삭제 + 락 해제, best-effort)
	void completeOrder(Long userId);

	// 3. 주문 실패 후 정리 (락만 해제 — 토큰 보존)
	void releaseOrderLock(Long userId);
}
