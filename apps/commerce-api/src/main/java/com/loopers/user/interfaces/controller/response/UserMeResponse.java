package com.loopers.user.interfaces.controller.response;

import com.loopers.user.application.dto.out.UserMeOutDto;

import java.time.LocalDate;

public record UserMeResponse(
	String loginId,
	String name,
	LocalDate birthday,
	String email
) {
	public static UserMeResponse from(UserMeOutDto outDto) {
		return new UserMeResponse(
			outDto.loginId(),
			maskName(outDto.name()),
			outDto.birthday(),
			outDto.email()
		);
	}

	private static String maskName(String name) {
		if (name == null || name.length() <= 1) {
			return "*";
		}
		return name.substring(0, name.length() - 1) + "*";
	}
}
