package com.loopers.support.common.auth;


import com.loopers.user.user.application.facade.UserQueryFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;


/**
 * 사용자 인증 공용 컴포넌트 (BC 외부에서 사용)
 * 1. 헤더 검증 + 사용자 인증 후 사용자 ID 반환 (필수 인증)
 * 2. 선택적 인증 — 헤더가 없으면 null 반환 (비로그인 허용)
 */

@Component
@RequiredArgsConstructor
public class AuthenticationResolver {

	// facade
	private final UserQueryFacade userQueryFacade;


	// 1. 헤더 검증 + 사용자 인증 후 사용자 ID 반환 (필수 인증)
	public Long resolve(String loginId, String password) {

		// 인증 헤더 필수값 검증
		HeaderValidator.validate(loginId, password);

		// 사용자 인증 후 사용자 ID 반환
		return userQueryFacade.authenticateAndGetUserId(loginId, password);
	}


	// 2. 선택적 인증 — 헤더가 없으면 null 반환 (비로그인 허용)
	public Long resolveOptional(String loginId, String password) {

		// 둘 다 null/blank → 비로그인 (null 반환, 예외 없음)
		if ((loginId == null || loginId.isBlank()) && (password == null || password.isBlank())) {
			return null;
		}

		// 하나라도 있으면 → 기존과 동일하게 필수 검증 + 인증
		HeaderValidator.validate(loginId, password);
		return userQueryFacade.authenticateAndGetUserId(loginId, password);
	}

}
