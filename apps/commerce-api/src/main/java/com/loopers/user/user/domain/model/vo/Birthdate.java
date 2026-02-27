package com.loopers.user.user.domain.model.vo;


import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;

import java.time.LocalDate;
import java.util.Objects;


/**
 * 생년월일 값 객체
 * - value: 생년월일 값
 */

public record Birthdate(LocalDate value) {

	private static final LocalDate MIN_BIRTH_DATE = LocalDate.of(1900, 1, 1);


	/**
	 * 도메인 로직
	 * 1. 생년월일 생성
	 * 2. DB 복원용 생년월일 생성
	 */

	// 1. 생년월일 생성
	public static Birthdate create(LocalDate rawBirthDate) {
		validate(rawBirthDate);
		return new Birthdate(rawBirthDate);
	}


	// 2. DB 복원용 생년월일 생성
	public static Birthdate from(LocalDate value) {
		return new Birthdate(Objects.requireNonNull(value, "birthdate value"));
	}


	/**
	 * private 메서드
	 * 1. 생년월일 검증
	 */

	// 1. 생년월일 검증
	private static void validate(LocalDate birthDate) {

		// 생년월일이 null인 경우
		if (birthDate == null) {
			throw new CoreException(ErrorType.INVALID_BIRTH_DATE, "생일을 입력해주세요.");
		}

		// 생년월일이 MIN_BIRTH_DATE보다 이전인 경우
		else if (birthDate.isBefore(MIN_BIRTH_DATE)) {
			throw new CoreException(ErrorType.INVALID_BIRTH_DATE).addMessage("생일은 1900년 1월 1일 이후여야 합니다.");
		}

		// 생년월일이 오늘 이후인 경우
		else if (!birthDate.isBefore(LocalDate.now())) {
			throw new CoreException(ErrorType.INVALID_BIRTH_DATE).addMessage("생일은 오늘 이후가 될 수 없습니다.");
		}
	}

}
