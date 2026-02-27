package com.loopers.engagement.productlike.application.port.out.client.user;


public interface UserAuthenticator {

	/**
	 * 사용자 인증 포트
	 * - Cross-BC: engagement(productlike) -> user
	 */

	// 1. 사용자 인증 후 ID 반환 (인증 실패 시 AUTHENTICATION_FAILED 예외)
	Long authenticate(String loginId, String password);

}
