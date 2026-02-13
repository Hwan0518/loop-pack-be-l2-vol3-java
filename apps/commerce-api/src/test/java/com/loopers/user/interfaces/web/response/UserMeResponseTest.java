package com.loopers.user.interfaces.web.response;

import com.loopers.user.application.dto.out.UserMeOutDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@DisplayName("UserMeResponse 테스트")
class UserMeResponseTest {

	private static final String VALID_LOGIN_ID = "testuser01";
	private static final LocalDate VALID_BIRTH_DATE = LocalDate.of(1990, 1, 15);
	private static final String VALID_EMAIL = "test@example.com";

	@Test
	@DisplayName("[UserMeResponse.from()] UserMeOutDto -> UserMeResponse 변환. loginId, maskedName, birthDate, email 매핑")
	void fromOutDtoThenMappedCorrectly() {
		// Arrange
		UserMeOutDto outDto = new UserMeOutDto(VALID_LOGIN_ID, "홍길동", VALID_BIRTH_DATE, VALID_EMAIL);

		// Act
		UserMeResponse response = UserMeResponse.from(outDto);

		// Assert
		assertAll(
			() -> assertThat(response.loginId()).isEqualTo(VALID_LOGIN_ID),
			() -> assertThat(response.name()).isEqualTo("홍길*"),
			() -> assertThat(response.birthDate()).isEqualTo(VALID_BIRTH_DATE),
			() -> assertThat(response.email()).isEqualTo(VALID_EMAIL)
		);
	}

	@Test
	@DisplayName("[UserMeResponse.from()] 이름 3자(홍길동) -> 마지막 글자 마스킹(홍길*)")
	void fromOutDtoNameMasking3Chars() {
		// Arrange
		UserMeOutDto outDto = new UserMeOutDto(VALID_LOGIN_ID, "홍길동", VALID_BIRTH_DATE, VALID_EMAIL);

		// Act
		UserMeResponse response = UserMeResponse.from(outDto);

		// Assert
		assertThat(response.name()).isEqualTo("홍길*");
	}

	@Test
	@DisplayName("[UserMeResponse.from()] 이름 2자(홍길) -> 마지막 글자 마스킹(홍*)")
	void fromOutDtoNameMasking2Chars() {
		// Arrange
		UserMeOutDto outDto = new UserMeOutDto(VALID_LOGIN_ID, "홍길", VALID_BIRTH_DATE, VALID_EMAIL);

		// Act
		UserMeResponse response = UserMeResponse.from(outDto);

		// Assert
		assertThat(response.name()).isEqualTo("홍*");
	}

	@Test
	@DisplayName("[UserMeResponse.from()] 이름 1자(김) -> 전체 마스킹(*)")
	void fromOutDtoNameMasking1Char() {
		// Arrange
		UserMeOutDto outDto = new UserMeOutDto(VALID_LOGIN_ID, "김", VALID_BIRTH_DATE, VALID_EMAIL);

		// Act
		UserMeResponse response = UserMeResponse.from(outDto);

		// Assert
		assertThat(response.name()).isEqualTo("*");
	}

	@Test
	@DisplayName("[UserMeResponse.from()] 이름 null -> null 반환")
	void fromOutDtoNameMaskingNull() {
		// Arrange
		UserMeOutDto outDto = new UserMeOutDto(VALID_LOGIN_ID, null, VALID_BIRTH_DATE, VALID_EMAIL);

		// Act
		UserMeResponse response = UserMeResponse.from(outDto);

		// Assert
		assertThat(response.name()).isNull();
	}

	@Test
	@DisplayName("[UserMeResponse.from()] 이름 빈 문자열(\"\") -> 빈 문자열 반환")
	void fromOutDtoNameMaskingEmptyString() {
		// Arrange
		UserMeOutDto outDto = new UserMeOutDto(VALID_LOGIN_ID, "", VALID_BIRTH_DATE, VALID_EMAIL);

		// Act
		UserMeResponse response = UserMeResponse.from(outDto);

		// Assert
		assertThat(response.name()).isEqualTo("");
	}

	@Test
	@DisplayName("[UserMeResponse.from()] 영문 이름(John) -> 마지막 글자 마스킹(Joh*)")
	void fromOutDtoNameMaskingEnglish() {
		// Arrange
		UserMeOutDto outDto = new UserMeOutDto(VALID_LOGIN_ID, "John", VALID_BIRTH_DATE, VALID_EMAIL);

		// Act
		UserMeResponse response = UserMeResponse.from(outDto);

		// Assert
		assertThat(response.name()).isEqualTo("Joh*");
	}
}
