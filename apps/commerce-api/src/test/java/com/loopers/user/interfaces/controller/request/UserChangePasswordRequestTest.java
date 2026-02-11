package com.loopers.user.interfaces.controller.request;


import com.loopers.user.interfaces.web.request.UserChangePasswordRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;


@DisplayName("UserChangePasswordRequest 테스트")
class UserChangePasswordRequestTest {

	@Test
	@DisplayName("[UserChangePasswordRequest] newPassword 필드 접근")
	void readNewPassword() {
		// Arrange
		UserChangePasswordRequest request = new UserChangePasswordRequest("NewPass1234!");

		// Act & Assert
		assertThat(request.newPassword()).isEqualTo("NewPass1234!");
	}

}
