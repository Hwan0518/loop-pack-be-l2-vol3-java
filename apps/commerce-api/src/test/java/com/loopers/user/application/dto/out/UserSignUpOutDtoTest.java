package com.loopers.user.application.dto.out;

import com.loopers.user.domain.model.User;
import com.loopers.user.domain.model.vo.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@DisplayName("UserSignUpOutDto 테스트")
class UserSignUpOutDtoTest {

	@Test
	@DisplayName("[UserSignUpOutDto.from()] User 도메인 객체 -> UserSignUpOutDto 변환. "
		+ "id, loginId, name, birthDate, email이 정확히 매핑됨")
	void from() {
		// Arrange
		Long id = 1L;
		String loginId = "testuser01";
		String encodedPassword = "encodedPassword";
		String name = "홍길동";
		LocalDate birthDate = LocalDate.of(1990, 1, 15);
		String email = "test@example.com";

		User user = User.reconstruct(id, LoginId.from(loginId), Password.from(encodedPassword),
			Name.from(name), Birthdate.from(birthDate), Email.from(email), null);

		// Act
		UserSignUpOutDto outDto = UserSignUpOutDto.from(user);

		// Assert
		assertAll(
			() -> assertThat(outDto).isNotNull(),
			() -> assertThat(outDto.id()).isEqualTo(id),
			() -> assertThat(outDto.loginId()).isEqualTo(loginId),
			() -> assertThat(outDto.name()).isEqualTo(name),
			() -> assertThat(outDto.birthDate()).isEqualTo(birthDate),
			() -> assertThat(outDto.email()).isEqualTo(email)
		);
	}
}
