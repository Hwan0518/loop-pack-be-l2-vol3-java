package com.loopers.queue.waitingqueue.application.port.out;


/**
 * 대기열 전용 서명 기반 인증 포트
 * - HMAC 검증만 수행, I/O 없음 (Redis 장애 시에도 동작)
 * - 추후 JWT 이식 시 구현체만 교체
 */
public interface QueueAuthPort {

	// 1. 서명 토큰에서 userId 추출 (HMAC 검증 + 만료 확인)
	Long resolveUserId(String token);

	// 2. userId 기반 서명 토큰 생성
	String generateToken(Long userId);

	// 3. 기존 토큰 갱신 (TTL 연장, Polling 응답 시 사용)
	String refreshToken(String token);
}
