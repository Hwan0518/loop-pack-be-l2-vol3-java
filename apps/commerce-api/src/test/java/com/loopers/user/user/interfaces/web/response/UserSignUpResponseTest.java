package com.loopers.user.user.interfaces.web.response;

import com.loopers.user.user.application.dto.out.UserSignUpOutDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.time.LocalDate;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@DisplayName("UserSignUpResponse 테스트")
class UserSignUpResponseTest {

	@Test
	@DisplayName("[UserSignUpResponse.from()] UserSignUpOutDto -> UserSignUpResponse 변환. "
		+ "id, loginId, name, birthDate, email이 정확히 매핑되며, 민감정보(password) 미포함")
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

		// Assert — 필드 매핑 검증 + 민감정보 제외 검증
		assertAll(
			() -> assertThat(response.id()).isEqualTo(id),
			() -> assertThat(response.loginId()).isEqualTo(loginId),
			() -> assertThat(response.name()).isEqualTo(name),
			() -> assertThat(response.birthDate()).isEqualTo(birthDate),
			() -> assertThat(response.email()).isEqualTo(email),
			// 민감정보(password) 미포함 보장 — 필드 추가 시 즉시 감지
			() -> assertThat(Arrays.stream(response.getClass().getRecordComponents())
				.map(RecordComponent::getName)
				.toList())
				.containsExactlyInAnyOrder("id", "loginId", "name", "birthDate", "email")
		);
	}
}
