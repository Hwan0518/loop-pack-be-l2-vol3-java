package com.loopers.user.interfaces.web.request;


import jakarta.validation.constraints.NotBlank;


/**
 * 비밀번호 변경 요청
 * - newPassword: 새 비밀번호
 */

public record UserChangePasswordRequest(
	@NotBlank(message = "새 비밀번호는 필수입니다.")
	String newPassword
) {}
