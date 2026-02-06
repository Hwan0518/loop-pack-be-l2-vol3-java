package com.loopers.user.application.dto.command;

public record UserChangePasswordCommand(
	String currentPassword,
	String newPassword
) {
}
