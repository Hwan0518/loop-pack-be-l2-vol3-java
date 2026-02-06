package com.loopers.user.domain.model.vo;

import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.regex.Pattern;

public record Password(String value) {

	private static final int MIN_LENGTH = 8;
	private static final int MAX_LENGTH = 16;
	private static final Pattern UPPERCASE_PATTERN = Pattern.compile("[A-Z]");
	private static final Pattern LOWERCASE_PATTERN = Pattern.compile("[a-z]");
	private static final Pattern DIGIT_PATTERN = Pattern.compile("[0-9]");
	private static final Pattern SPECIAL_CHAR_PATTERN = Pattern.compile("[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]");
	private static final Pattern ALLOWED_CHARS_PATTERN = Pattern.compile("^[a-zA-Z0-9!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]+$");

	public static Password create(String rawPassword, LocalDate birthday) {
		validateFormat(rawPassword);
		validateNotContainsBirthday(rawPassword, birthday);
		return new Password(encode(rawPassword));
	}

	public static Password fromEncoded(String encodedPassword) {
		return new Password(encodedPassword);
	}

	public boolean matches(String rawPassword) {
		if (rawPassword == null) {
			return false;
		}
		return this.value.equals(encode(rawPassword));
	}

	private static void validateFormat(String rawPassword) {
		if (rawPassword == null ||
			rawPassword.length() < MIN_LENGTH ||
			rawPassword.length() > MAX_LENGTH ||
			!ALLOWED_CHARS_PATTERN.matcher(rawPassword).matches() ||
			!UPPERCASE_PATTERN.matcher(rawPassword).find() ||
			!LOWERCASE_PATTERN.matcher(rawPassword).find() ||
			!DIGIT_PATTERN.matcher(rawPassword).find() ||
			!SPECIAL_CHAR_PATTERN.matcher(rawPassword).find()) {
			throw new CoreException(ErrorType.INVALID_PASSWORD_FORMAT);
		}
	}

	private static void validateNotContainsBirthday(String rawPassword, LocalDate birthday) {
		String yyyymmdd = birthday.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
		String yymmdd = birthday.format(DateTimeFormatter.ofPattern("yyMMdd"));
		String yyyyDashMmDashDd = birthday.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

		if (rawPassword.contains(yyyymmdd) || rawPassword.contains(yymmdd) || rawPassword.contains(yyyyDashMmDashDd)) {
			throw new CoreException(ErrorType.PASSWORD_CONTAINS_BIRTHDAY);
		}
	}

	private static String encode(String rawPassword) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(rawPassword.getBytes(StandardCharsets.UTF_8));
			return Base64.getEncoder().encodeToString(hash);
		} catch (NoSuchAlgorithmException e) {
			throw new CoreException(ErrorType.INTERNAL_ERROR);
		}
	}
}
