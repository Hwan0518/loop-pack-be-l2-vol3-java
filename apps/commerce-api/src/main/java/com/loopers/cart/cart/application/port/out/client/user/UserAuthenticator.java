package com.loopers.cart.cart.application.port.out.client.user;


public interface UserAuthenticator {

	/**
	 * 사용자 인증 포트
	 * - Cross-BC: cart -> user
	 * - 구현체는 ACL 레이어에서 제공
	 */

	// 1. 사용자 인증 후 ID 반환 (인증 실패 시 AUTHENTICATION_FAILED 예외)
	Long authenticate(String loginId, String password);

}
