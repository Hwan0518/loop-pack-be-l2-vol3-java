package com.loopers.user.domain.model.vo;


import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.regex.Pattern;


/**
 * 비밀번호 값 객체
 * - value: 인코딩된 비밀번호 해시 값
 */

public record Password(String value) {

	private static final int MIN_LENGTH = 8;
	private static final int MAX_LENGTH = 16;
	private static final Pattern UPPERCASE_PATTERN = Pattern.compile("[A-Z]");
	private static final Pattern LOWERCASE_PATTERN = Pattern.compile("[a-z]");
	private static final Pattern DIGIT_PATTERN = Pattern.compile("[0-9]");
	private static final Pattern SPECIAL_CHAR_PATTERN = Pattern.compile("[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]");
	private static final Pattern ALLOWED_CHARS_PATTERN = Pattern.compile(
		"^[a-zA-Z0-9!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]+$");


	/**
	 * 도메인 로직
	 * 1. 검증된 비밀번호로 Password 생성
	 * 2. 비밀번호 정책 검증
	 */

	// 1. 검증된 비밀번호로 Password 생성
	public static Password from(String encodedPassword) {
		return new Password(Objects.requireNonNull(encodedPassword, "encodedPassword value"));
	}


	// 2. 비밀번호 정책 검증
	public static void validatePasswordPolicy(String rawPassword, LocalDate birthDate) {

		// 형식 검증
		validateFormat(rawPassword);

		// 생년월일 미포함 검증
		validateNotContainsBirthDate(rawPassword, birthDate);
	}


	/**
	 * private 메서드
	 * 1. 비밀번호 형식 검증
	 * 2. 비밀번호에 생년월일 미포함 검증
	 */

	// 1. 비밀번호 형식 검증
	private static void validateFormat(String rawPassword) {

		// null/빈 값 검증
		if (rawPassword == null || rawPassword.isBlank() || rawPassword.isEmpty()) {
			throw new CoreException(ErrorType.INVALID_PASSWORD_FORMAT, "비밀번호를 입력해주세요.");
		}

		// 길이/허용문자/문자 조합 규칙 검증
		else if (rawPassword.length() < MIN_LENGTH ||
			rawPassword.length() > MAX_LENGTH ||
			!ALLOWED_CHARS_PATTERN.matcher(rawPassword).matches() ||
			!UPPERCASE_PATTERN.matcher(rawPassword).find() ||
			!LOWERCASE_PATTERN.matcher(rawPassword).find() ||
			!DIGIT_PATTERN.matcher(rawPassword).find() ||
			!SPECIAL_CHAR_PATTERN.matcher(rawPassword).find()) {
			throw new CoreException(ErrorType.INVALID_PASSWORD_FORMAT);
		}
	}


	// 2. 비밀번호에 생년월일 미포함 검증
	private static void validateNotContainsBirthDate(String rawPassword, LocalDate birthDate) {

		// 생년월일 패턴 후보 생성
		String yyyymmdd = birthDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
		String yymmdd = birthDate.format(DateTimeFormatter.ofPattern("yyMMdd"));
		String yyyyDashMmDashDd = birthDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

		// 비밀번호에 생년월일이 포함되면 예외 반환
		if (rawPassword.contains(yyyymmdd) || rawPassword.contains(yymmdd) || rawPassword.contains(yyyyDashMmDashDd)) {
			throw new CoreException(ErrorType.PASSWORD_CONTAINS_BIRTH_DATE);
		}
	}

}
