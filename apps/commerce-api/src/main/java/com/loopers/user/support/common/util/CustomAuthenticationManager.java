package com.loopers.user.support.common.util;


import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import com.loopers.user.application.port.AuthenticationManager;
import com.loopers.user.application.port.PasswordEncoder;
import com.loopers.user.domain.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
public class CustomAuthenticationManager implements AuthenticationManager {

	// util
	private final PasswordEncoder passwordEncoder;


	/**
	 * 인증 매니저
	 * 1. 현재 비밀번호와 입력 비밀번호 일치 여부 검증
	 */

	// 1. 현재 비밀번호와 입력 비밀번호 일치 여부 검증
	@Override
	public void authenticate(User user, String inputPassword) {

		// 불일치 하면 예외처리
		if (!passwordEncoder.matches(inputPassword, user.getPassword().value())) {
			throw new CoreException(ErrorType.AUTHENTICATION_FAILED);
		}
	}

}
