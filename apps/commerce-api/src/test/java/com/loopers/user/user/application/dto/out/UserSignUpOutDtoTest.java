package com.loopers.user.user.application.dto.out;

import com.loopers.user.user.domain.model.User;
import com.loopers.user.user.domain.model.vo.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.time.LocalDate;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@DisplayName("UserSignUpOutDto 테스트")
class UserSignUpOutDtoTest {

	@Test
	@DisplayName("[UserSignUpOutDto.from()] User 도메인 객체 -> UserSignUpOutDto 변환. "
		+ "id, loginId, name, birthDate, email이 정확히 매핑되며, 민감정보(password) 미포함")
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

		// Assert — Value Object 추출 검증 + 민감정보 제외 검증
		assertAll(
			() -> assertThat(outDto.id()).isEqualTo(id),
			() -> assertThat(outDto.loginId()).isEqualTo(loginId),
			() -> assertThat(outDto.name()).isEqualTo(name),
			() -> assertThat(outDto.birthDate()).isEqualTo(birthDate),
			() -> assertThat(outDto.email()).isEqualTo(email),
			// 민감정보(password) 미포함 보장 — 필드 추가 시 즉시 감지
			() -> assertThat(Arrays.stream(outDto.getClass().getRecordComponents())
				.map(RecordComponent::getName)
				.toList())
				.containsExactlyInAnyOrder("id", "loginId", "name", "birthDate", "email")
		);
	}
}
