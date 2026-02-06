package com.loopers.user.application.dto.out;

import com.loopers.user.domain.model.User;

import java.time.LocalDate;

public record UserSignUpOutDto(
	Long id,
	String loginId,
	String name,
	LocalDate birthday,
	String email
) {
	public static UserSignUpOutDto from(User user) {
		return new UserSignUpOutDto(
			user.getId(),
			user.getLoginId(),
			user.getName(),
			user.getBirthday(),
			user.getEmail()
		);
	}
}
