package com.loopers.user.user.interfaces.web.response;

import com.loopers.user.user.application.dto.out.UserSignUpOutDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@DisplayName("UserSignUpResponse 테스트")
class UserSignUpResponseTest {

	@Test
	@DisplayName("[UserSignUpResponse.from()] UserSignUpOutDto -> UserSignUpResponse 변환. "
		+ "id, loginId, name, birthDate, email이 정확히 매핑됨")
	void from() {
		// Arrange
		Long id = 1L;
		String loginId = "testuser01";
		String name = "홍길동";
		LocalDate birthDate = LocalDate.of(1990, 1, 15);
		String email = "test@example.com";

		UserSignUpOutDto outDto = new UserSignUpOutDto(id, loginId, name, birthDate, email);

		// Act
		UserSignUpResponse response = UserSignUpResponse.from(outDto);

		// Assert
		assertAll(
			() -> assertThat(response).isNotNull(),
			() -> assertThat(response.id()).isEqualTo(id),
			() -> assertThat(response.loginId()).isEqualTo(loginId),
			() -> assertThat(response.name()).isEqualTo(name),
			() -> assertThat(response.birthDate()).isEqualTo(birthDate),
			() -> assertThat(response.email()).isEqualTo(email)
		);
	}
}
