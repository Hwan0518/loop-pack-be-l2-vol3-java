package com.loopers.user.application.port;


public interface PasswordEncoder {

	/**
	 * 비밀번호 인코딩 포트
	 * - encode: 원문 비밀번호 인코딩
	 * - matches: 원문 비밀번호와 인코딩된 비밀번호 일치 여부 검증
	 * - checkPasswordDuplication: 현재 비밀번호와 새 비밀번호 중복 검증
	 */

	String encode(String rawPassword);

	boolean matches(String rawPassword, String encodedPassword);

	void checkPasswordDuplication(String curPasswordHash, String newPassword);

}
