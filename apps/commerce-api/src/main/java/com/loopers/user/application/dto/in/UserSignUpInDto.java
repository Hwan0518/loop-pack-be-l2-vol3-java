package com.loopers.user.application.dto.in;

import java.time.LocalDate;

public record UserSignUpInDto(
	String loginId,
	String password,
	String name,
	LocalDate birthday,
	String email
) {
}
