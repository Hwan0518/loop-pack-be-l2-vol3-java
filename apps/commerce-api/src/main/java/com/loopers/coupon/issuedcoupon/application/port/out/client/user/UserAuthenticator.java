package com.loopers.coupon.issuedcoupon.application.port.out.client.user;


/**
 * 사용자 인증 포트 (coupon BC → user BC)
 * - Cross-BC: coupon → user
 * - 구현체는 ACL 레이어에서 제공
 */
public interface UserAuthenticator {

	// 1. 사용자 인증 후 사용자 ID 반환 (인증 실패 시 AUTHENTICATION_FAILED 예외)
	Long authenticate(String loginId, String password);

}
