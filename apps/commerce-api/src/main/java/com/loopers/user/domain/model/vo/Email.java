package com.loopers.user.domain.model.vo;


import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;

import java.util.regex.Pattern;


/**
 * 이메일 값 객체
 * - value: 정규화된 이메일 값
 */

public record Email(String value) {

	private static final int MAX_LENGTH = 254;
	private static final Pattern PATTERN = Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");
	private static final Pattern DOMAIN_INVALID_PATTERN = Pattern.compile("(\\.\\.|^\\.|\\.$)");


	/**
	 * 도메인 로직
	 * 1. 이메일 생성
	 * 2. DB 복원용 이메일 생성
	 * 3. 이메일 정규화
	 */

	// 1. 이메일 생성
	public static Email create(String rawEmail) {
		String normalized = normalize(rawEmail);
		validate(normalized);
		return new Email(normalized);
	}


	// 2. DB 복원용 이메일 생성
	public static Email from(String value) {
		return new Email(value);
	}


	// 3. 이메일 정규화
	public static String normalize(String email) {
		if (email == null) {
			return null;
		}
		String trimmed = email.trim();
		return trimmed.isBlank() ? null : trimmed;
	}


	/**
	 * private 메서드
	 * 1. 이메일 형식 검증
	 */

	// 1. 이메일 형식 검증
	private static void validate(String email) {

		if (email == null ||
			email.length() > MAX_LENGTH ||
			!PATTERN.matcher(email).matches()
		) {
			throw new CoreException(ErrorType.INVALID_EMAIL_FORMAT);
		}

		// 도메인 비정상 패턴 검증
		String domain = email.substring(email.indexOf('@') + 1);
		if (DOMAIN_INVALID_PATTERN.matcher(domain).find()) {
			throw new CoreException(ErrorType.INVALID_EMAIL_FORMAT);
		}
	}

}
