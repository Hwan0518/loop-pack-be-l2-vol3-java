package com.loopers.user.application.port.out.util;


public interface PasswordEncoder {

	/**
	 * 비밀번호 인코딩 포트
	 * 1. 원문 비밀번호 인코딩
	 * 2. 원문 비밀번호와 인코딩된 비밀번호 일치 여부 검증
	 * 3. 현재 비밀번호와 새 비밀번호 중복 검증
	 */

	// 1. 원문 비밀번호 인코딩
	String encode(String rawPassword);

	// 2. 원문 비밀번호와 인코딩된 비밀번호 일치 여부 검증
	boolean matches(String rawPassword, String encodedPassword);

	// 3. 현재 비밀번호와 새 비밀번호 중복 검증
	void checkPasswordDuplication(String curPasswordHash, String newPassword);

}
