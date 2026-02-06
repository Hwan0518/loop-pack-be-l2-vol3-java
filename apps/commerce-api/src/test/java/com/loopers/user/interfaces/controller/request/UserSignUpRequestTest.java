package com.loopers.user.interfaces.controller.request;

import com.loopers.user.application.dto.command.UserSignUpCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@DisplayName("UserSignUpRequest 테스트")
class UserSignUpRequestTest {

	@Test
	@DisplayName("[UserSignUpRequest.toCommand()] 유효한 Request -> UserSignUpCommand 변환. "
		+ "모든 필드(loginId, password, name, birthday, email)가 정확히 매핑됨")
	void toCommand() {
		// Arrange
		String loginId = "testuser01";
		String password = "Test1234!";
		String name = "홍길동";
		LocalDate birthday = LocalDate.of(1990, 1, 15);
		String email = "test@example.com";

		UserSignUpRequest request = new UserSignUpRequest(loginId, password, name, birthday, email);

		// Act
		UserSignUpCommand command = request.toCommand();

		// Assert
		assertAll(
			() -> assertThat(command).isNotNull(),
			() -> assertThat(command.loginId()).isEqualTo(loginId),
			() -> assertThat(command.password()).isEqualTo(password),
			() -> assertThat(command.name()).isEqualTo(name),
			() -> assertThat(command.birthday()).isEqualTo(birthday),
			() -> assertThat(command.email()).isEqualTo(email)
		);
	}
}
