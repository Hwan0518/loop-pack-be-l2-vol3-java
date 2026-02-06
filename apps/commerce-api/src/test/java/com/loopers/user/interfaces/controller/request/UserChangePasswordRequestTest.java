package com.loopers.user.interfaces.controller.request;

import com.loopers.user.application.dto.command.UserChangePasswordCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@DisplayName("UserChangePasswordRequest 테스트")
class UserChangePasswordRequestTest {

	@Test
	@DisplayName("[toCommand()] 유효한 Request -> UserChangePasswordCommand 변환. "
		+ "currentPassword, newPassword 필드가 정확히 매핑됨")
	void toCommand() {
		// Arrange
		String currentPassword = "Test1234!";
		String newPassword = "NewPass1234!";

		UserChangePasswordRequest request = new UserChangePasswordRequest(currentPassword, newPassword);

		// Act
		UserChangePasswordCommand command = request.toCommand();

		// Assert
		assertAll(
			() -> assertThat(command).isNotNull(),
			() -> assertThat(command.currentPassword()).isEqualTo(currentPassword),
			() -> assertThat(command.newPassword()).isEqualTo(newPassword)
		);
	}
}
