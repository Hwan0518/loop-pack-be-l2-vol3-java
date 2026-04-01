package com.loopers.queue.waitingqueue.application.port.out;


/**
 * 대기열 전용 서명 기반 인증 포트
 * - HMAC 검증만 수행, I/O 없음 (Redis 장애 시에도 동작)
 * - 추후 JWT 이식 시 구현체만 교체
 */
public interface QueueAuthPort {

	// 검증 + 갱신 결과 (userId 추출 + 새 토큰 발급을 원자적으로 수행)
	record ResolvedQueueToken(Long userId, String refreshedToken) {}

	// 1. 서명 토큰 검증 + userId 추출 + 갱신된 토큰 발급 (단일 호출로 이중 검증 방지)
	ResolvedQueueToken resolveAndRefresh(String token);

	// 2. userId 기반 서명 토큰 생성
	String generateToken(Long userId);

	// 3. 진입 증명 생성 (enter 성공 시 발급, 상태 증명용)
	String generateEnterReceipt(Long userId);

	// 4. 진입 증명 검증 (유효한 receipt면 userId 반환, 무효/만료 시 null)
	Long resolveEnterReceipt(String receipt);
}
