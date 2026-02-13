package com.loopers.user.interfaces.web.response;


import com.loopers.user.application.dto.out.UserMeOutDto;

import java.time.LocalDate;


/**
 * 내 정보 조회 응답
 * - loginId: 로그인 ID
 * - name: 마스킹된 이름
 * - birthDate: 생년월일
 * - email: 이메일
 */

public record UserMeResponse(
	String loginId,
	String name,
	LocalDate birthDate,
	String email
) {

	// 1. 내 정보 조회 결과 DTO를 컨트롤러 응답 객체로 변환
	public static UserMeResponse from(UserMeOutDto outDto) {
		return new UserMeResponse(
			outDto.loginId(),
			maskName(outDto.name()),
			outDto.birthDate(),
			outDto.email()
		);
	}


	// 2. 이름 마지막 글자를 마스킹 처리
	private static String maskName(String name) {

		// 이름이 null이거나 공백인 경우 그대로 반환
		if (name == null || name.isBlank()) {
			return name;
		}

		// 이름이 한 글자인 경우 '*'로 반환
		else if (name.length() <= 1) {
			return "*";
		}

		// 이외의 경우 이름의 마지막 글자를 '*'로 마스킹 처리
		return name.substring(0, name.length() - 1) + "*";
	}

}
