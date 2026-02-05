package com.loopers.user.application.dto.command;

import java.time.LocalDate;

public record UserSignUpCommand(
	String loginId,
	String password,
	String name,
	LocalDate birthday,
	String email
) {
}
