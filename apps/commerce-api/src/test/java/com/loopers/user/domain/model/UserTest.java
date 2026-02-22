package com.loopers.user.domain.model;


import com.loopers.user.domain.model.vo.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;


@DisplayName("User 도메인 모델 테스트")
class UserTest {

	private static final LoginId VALID_LOGIN_ID = LoginId.create("testuser01");
	private static final Password VALID_PASSWORD = Password.from("encodedPassword");
	private static final Name VALID_NAME = Name.create("홍길동");
	private static final LocalDate VALID_BIRTH_DATE_RAW = LocalDate.of(1990, 1, 15);
	private static final Birthdate VALID_BIRTH_DATE = Birthdate.create(VALID_BIRTH_DATE_RAW);
	private static final Email VALID_EMAIL = Email.create("test@example.com");

	@Nested
	@DisplayName("생성 테스트")
	class CreateTest {

		@Test
		@DisplayName("[User.create()] 유효한 VO로 User 생성 -> User 객체 반환. "
			+ "모든 필드가 정상적으로 설정됨")
		void createWithValidInfo() {
			// Act
			User user = User.create(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);

			// Assert
			assertAll(
				() -> assertThat(user).isNotNull(),
				() -> assertThat(user.getId()).isNull(),
				() -> assertThat(user.getLoginId().value()).isEqualTo("testuser01"),
				() -> assertThat(user.getPassword().value()).isEqualTo("encodedPassword"),
				() -> assertThat(user.getName().value()).isEqualTo("홍길동"),
				() -> assertThat(user.getBirthDate().value()).isEqualTo(VALID_BIRTH_DATE_RAW),
				() -> assertThat(user.getEmail().value()).isEqualTo("test@example.com")
			);
		}

	}

	@Nested
	@DisplayName("재구성 테스트")
	class ReconstructTest {

		@Test
		@DisplayName("[User.reconstruct()] 저장된 데이터로 User 재구성 -> User 객체 반환. "
			+ "모든 필드가 정확히 복원됨")
		void reconstructFromStoredData() {
			// Arrange
			Long id = 1L;

			// Act
			User user = User.reconstruct(id, LoginId.from("testuser01"), Password.from("encodedPw"),
				Name.from("홍길동"), Birthdate.from(VALID_BIRTH_DATE_RAW), Email.from("test@example.com"), null);

			// Assert
			assertAll(
				() -> assertThat(user.getId()).isEqualTo(id),
				() -> assertThat(user.getLoginId().value()).isEqualTo("testuser01"),
				() -> assertThat(user.getPassword().value()).isEqualTo("encodedPw"),
				() -> assertThat(user.getName().value()).isEqualTo("홍길동"),
				() -> assertThat(user.getBirthDate().value()).isEqualTo(VALID_BIRTH_DATE_RAW),
				() -> assertThat(user.getEmail().value()).isEqualTo("test@example.com"),
				() -> assertThat(user.getDeletedAt()).isNull()
			);
		}

	}

	@Nested
	@DisplayName("비밀번호 변경 테스트")
	class ChangePasswordTest {

		@Test
		@DisplayName("[changePassword()] 인코딩된 새 비밀번호 -> 비밀번호 변경 성공")
		void changePasswordSuccess() {
			// Arrange
			User user = User.create(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);
			String encodedNewPassword = "newEncodedPassword";

			// Act
			user.changePassword(encodedNewPassword);

			// Assert
			assertThat(user.getPassword().value()).isEqualTo(encodedNewPassword);
		}

	}

}
