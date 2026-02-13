package com.loopers.user.application.port;


import com.loopers.user.domain.model.User;


public interface AuthenticationManager {

	/**
	 * 인증 매니저
	 * - authenticate: 현재 비밀번호와 입력 비밀번호 일치 여부 검증
	 */

	void authenticate(User user, String inputPassword);

}
