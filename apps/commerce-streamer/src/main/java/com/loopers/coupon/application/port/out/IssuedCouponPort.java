package com.loopers.coupon.application.port.out;


/**
 * 발급된 쿠폰 관리 포트 (application → infrastructure 계약)
 * - 중복 발급 검사, 발급 수량 조회, 신규 발급
 */
public interface IssuedCouponPort {

	// 1. 중복 발급 검사
	boolean existsByUserIdAndTemplateId(Long userId, Long couponTemplateId);

	// 2. 템플릿별 발급 수량 조회
	long countByTemplateId(Long couponTemplateId);

	// 3. 쿠폰 발급 (신규 INSERT)
	void issue(Long userId, Long couponTemplateId);

}
