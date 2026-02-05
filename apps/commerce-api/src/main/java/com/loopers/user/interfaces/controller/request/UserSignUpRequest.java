package com.loopers.user.interfaces.controller.request;

import com.loopers.user.application.dto.command.UserSignUpCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record UserSignUpRequest(
	@NotBlank(message = "로그인 ID는 필수입니다.")
	String loginId,

	@NotBlank(message = "비밀번호는 필수입니다.")
	String password,

	@NotBlank(message = "이름은 필수입니다.")
	String name,

	@NotNull(message = "생년월일은 필수입니다.")
	LocalDate birthday,

	@NotBlank(message = "이메일은 필수입니다.")
	String email
) {
	public UserSignUpCommand toCommand() {
		return new UserSignUpCommand(loginId, password, name, birthday, email);
	}
}
