package com.loopers.queue.waitingqueue.application.port.out;


/**
 * 대기열 Redis 연산 포트
 * - master-only RedisTemplate 사용 (stale read 방지)
 * - INCR, ZADD, ZRANK, ZCARD, ZREM 연산
 */
public interface WaitingQueueRedisPort {

	// 1. 대기열 적재 (INCR → ZADD, 전역 단조 증가 순번)
	long addToQueue(Long userId);

	// 2. 순번 조회 (ZRANK, null이면 대기열 미존재)
	Long getPosition(Long userId);

	// 3. 전체 대기 인원 (ZCARD)
	long getQueueSize();

	// 4. 대기열 제거 (ZREM, cap 초과 시)
	void removeFromQueue(Long userId);

	// 5. 입장 토큰 조회 (GET entry-token:{userId})
	String getEntryToken(Long userId);

	// 6. 입장 토큰 발급 — Lua script (ZPOPMIN + SET EX, 원자적)
	java.util.List<String> issueEntryTokens(int batchSize, long tokenTtlSeconds);

	// 7. 입장 토큰 삭제 (DEL entry-token:{userId}, 주문 완료 후)
	void deleteEntryToken(Long userId);

	// 8. cap 초과 거부 상태 저장 (SET queue-rejected:{userId}, TTL 60초)
	void markRejected(Long userId);

	// 9. cap 초과 거부 상태 확인
	boolean isRejected(Long userId);

	// 10. 주문 처리 락 획득 (SET NX EX — 동시 주문 방지)
	boolean acquireOrderLock(Long userId, long ttlSeconds);

	// 11. 주문 처리 락 해제 (DEL — 주문 완료/실패 후)
	void releaseOrderLock(Long userId);
}
