package com.loopers.user.application.dto.in;


/**
 * 비밀번호 변경 입력 DTO
 * - loginId: 로그인 ID
 * - currentPassword: 현재 비밀번호
 * - newPassword: 새 비밀번호
 */
public record UserChangePasswordInDto(
	String loginId,
	String currentPassword,
	String newPassword
) {
}
