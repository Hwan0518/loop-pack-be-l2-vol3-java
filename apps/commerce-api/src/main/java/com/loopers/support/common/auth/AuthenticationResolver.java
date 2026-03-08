package com.loopers.support.common.auth;


import com.loopers.user.user.application.facade.UserQueryFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;


/**
 * 사용자 인증 공용 컴포넌트 (BC 외부에서 사용)
 * 1. 헤더 검증 + 사용자 인증 후 사용자 ID 반환
 */

@Component
@RequiredArgsConstructor
public class AuthenticationResolver {

	// facade
	private final UserQueryFacade userQueryFacade;


	// 1. 헤더 검증 + 사용자 인증 후 사용자 ID 반환
	public Long resolve(String loginId, String password) {

		// 인증 헤더 필수값 검증
		HeaderValidator.validate(loginId, password);

		// 사용자 인증 후 사용자 ID 반환
		return userQueryFacade.authenticateAndGetUserId(loginId, password);
	}

}
