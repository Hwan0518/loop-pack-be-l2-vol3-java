package com.loopers.user.user.support.common.util;


import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import com.loopers.user.user.application.port.out.util.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;


@Component
public class Sha256PasswordEncoder implements PasswordEncoder {

	/**
	 * SHA-256 비밀번호 인코더: SHA-256 해시 + Base64 인코딩 방식으로 비밀번호를 인코딩
	 * 1. 비밀번호 인코딩
	 * 2. 비밀번호 일치 여부 검증
	 * 3. 비밀번호 중복 검증
	 * 4. 새 비밀번호 형식 검증
	 * 5. 비밀번호 인증
	 */

	// 1. 비밀번호 인코딩
	@Override
	public String encode(String rawPassword) {

		// 입력값 인코딩
		try {
			// SHA-256 해시 생성
			MessageDigest digest = MessageDigest.getInstance("SHA-256");

			// 바이트 배열로 변환 후 해시 계산
			byte[] hash = digest.digest(rawPassword.getBytes(StandardCharsets.UTF_8));

			// Base64 인코딩 후 문자열 반환
			return Base64.getEncoder().encodeToString(hash);
		}

		// 예외 처리
		catch (NoSuchAlgorithmException e) {
			throw new CoreException(ErrorType.INTERNAL_ERROR);
		}
	}


	// 2. 비밀번호 일치 여부 검증
	@Override
	public boolean matches(String rawPassword, String encodedPassword) {

		// 원문 비밀번호 인코딩
		String encodedRawPassword = encode(rawPassword);

		// 인코딩된 비밀번호와 비교
		return encodedRawPassword.equals(encodedPassword);
	}


	// 3. 비밀번호 중복 검증
	@Override
	public void checkPasswordDuplication(String curPasswordHash, String newPassword) {

		// 현재 비밀번호와 동일하면 예외처리
		if (matches(newPassword, curPasswordHash)) {
			throw new CoreException(ErrorType.PASSWORD_SAME_AS_CURRENT);
		}
	}

}
