package com.loopers.user.interfaces.controller.request;

import com.loopers.user.application.dto.command.UserChangePasswordCommand;
import jakarta.validation.constraints.NotBlank;

public record UserChangePasswordRequest(
	@NotBlank(message = "현재 비밀번호는 필수입니다.")
	String currentPassword,

	@NotBlank(message = "새 비밀번호는 필수입니다.")
	String newPassword
) {
	public UserChangePasswordCommand toCommand() {
		return new UserChangePasswordCommand(currentPassword, newPassword);
	}
}
