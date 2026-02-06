package com.loopers.user.application.dto.in;

public record UserChangePasswordInDto(
	String currentPassword,
	String newPassword
) {
}
