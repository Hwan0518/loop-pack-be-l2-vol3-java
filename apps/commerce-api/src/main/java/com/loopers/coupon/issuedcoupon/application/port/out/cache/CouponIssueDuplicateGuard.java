package com.loopers.coupon.issuedcoupon.application.port.out.cache;


/**
 * 쿠폰 발급 중복 방어 로컬 캐시 (1차 방어)
 * - 동일 사용자의 동일 쿠폰 템플릿 중복 발급을 로컬 캐시로 빠르게 차단
 * - DB 복합 유니크 제약(2차 방어)과 함께 이중 방어 구조를 구성
 */
public interface CouponIssueDuplicateGuard {

	/**
	 * 발급 락 획득 시도
	 * @param userId 사용자 ID
	 * @param couponTemplateId 쿠폰 템플릿 ID
	 * @return true: 최초 요청 (발급 진행 가능), false: 중복 요청 (발급 차단)
	 */
	boolean tryAcquire(Long userId, Long couponTemplateId);

	/**
	 * 발급 락 해제 (발급 실패 시 캐시 점유 해제)
	 * - 가드 획득 후 검증/저장 실패 시 호출하여, 실제 발급되지 않은 요청이 캐시에 남아 차단되는 것을 방지
	 * @param userId 사용자 ID
	 * @param couponTemplateId 쿠폰 템플릿 ID
	 */
	void release(Long userId, Long couponTemplateId);

}
