package com.loopers.user.interfaces.controller.response;

import com.loopers.user.application.dto.out.UserSignUpOutDto;

import java.time.LocalDate;

public record UserSignUpResponse(
	Long id,
	String loginId,
	String name,
	LocalDate birthday,
	String email
) {
	public static UserSignUpResponse from(UserSignUpOutDto outDto) {
		return new UserSignUpResponse(
			outDto.id(),
			outDto.loginId(),
			outDto.name(),
			outDto.birthday(),
			outDto.email()
		);
	}
}
