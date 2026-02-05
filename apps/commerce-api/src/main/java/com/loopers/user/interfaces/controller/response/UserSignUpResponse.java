package com.loopers.user.interfaces.controller.response;

import com.loopers.user.domain.model.User;

import java.time.LocalDate;

public record UserSignUpResponse(
	Long id,
	String loginId,
	String name,
	LocalDate birthday,
	String email
) {
	public static UserSignUpResponse from(User user) {
		return new UserSignUpResponse(
			user.getId(),
			user.getLoginId(),
			user.getName(),
			user.getBirthday(),
			user.getEmail()
		);
	}
}
