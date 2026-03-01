package com.loopers.user.user.application.port.out.util;


import com.loopers.user.user.domain.model.User;


public interface AuthenticationManager {

	/**
	 * 인증 매니저
	 * 1. 현재 비밀번호와 입력 비밀번호 일치 여부 검증
	 */

	// 1. 현재 비밀번호와 입력 비밀번호 일치 여부 검증
	void authenticate(User user, String inputPassword);

}
