package com.loopers.ordering.order.application.port.out.client.queue;


/**
 * 주문 시 입장 토큰 소비 포트 (Cross-BC: ordering → queue)
 * - 입장 토큰 원자적 소비 (검증 + 삭제)
 */
public interface OrderEntryTokenValidator {

	// 1. 입장 토큰 원자적 소비 (GETDEL — 검증 + 삭제, 불일치 시 예외)
	void consume(Long userId, String entryToken);
}
