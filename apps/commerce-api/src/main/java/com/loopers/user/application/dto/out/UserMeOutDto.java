package com.loopers.user.application.dto.out;

import com.loopers.user.domain.model.User;

import java.time.LocalDate;

public record UserMeOutDto(
	String loginId,
	String name,
	LocalDate birthday,
	String email
) {
	public static UserMeOutDto from(User user) {
		return new UserMeOutDto(
			user.getLoginId(),
			user.getName(),
			user.getBirthday(),
			user.getEmail()
		);
	}
}
