package com.loopers.user.domain.model.vo;


import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;

import java.util.Objects;
import java.util.regex.Pattern;


/**
 * 이름 값 객체
 * - value: 정규화된 이름 값
 */

public record Name(String value) {

	private static final int MAX_LENGTH = 50;
	private static final Pattern PATTERN = Pattern.compile("^[가-힣a-zA-Z ]+$");


	/**
	 * 도메인 로직
	 * 1. 이름 생성
	 * 2. DB 복원용 이름 생성
	 * 3. 이름 정규화
	 */

	// 1. 이름 생성
	public static Name create(String rawName) {
		String normalized = normalize(rawName);
		validate(normalized);
		return new Name(normalized);
	}


	// 2. DB 복원용 이름 생성
	public static Name from(String value) {
		return new Name(Objects.requireNonNull(value, "name value"));
	}


	// 3. 이름 정규화
	public static String normalize(String name) {
		if (name == null) {
			return null;
		}
		String trimmed = name.trim();
		return trimmed.isBlank() ? null : trimmed;
	}


	/**
	 * private 메서드
	 * 1. 이름 형식 검증
	 */

	// 1. 이름 형식 검증
	private static void validate(String name) {

		if (name == null ||
			name.length() > MAX_LENGTH ||
			!PATTERN.matcher(name).matches()
		) {
			throw new CoreException(ErrorType.INVALID_NAME_FORMAT);
		}
	}

}
