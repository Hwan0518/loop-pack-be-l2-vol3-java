package com.loopers.user.domain.model;


import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import com.loopers.user.domain.model.vo.LoginId;
import com.loopers.user.domain.model.vo.Password;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;


@DisplayName("User 도메인 모델 테스트")
class UserTest {

	private static final String VALID_LOGIN_ID = "testuser01";
	private static final String VALID_ENCODED_PASSWORD = "encodedPassword";
	private static final String VALID_NAME = "홍길동";
	private static final LocalDate VALID_BIRTHDAY = LocalDate.of(1990, 1, 15);
	private static final String VALID_EMAIL = "test@example.com";


	private User createValidUser() {
		return User.create(
			LoginId.create(VALID_LOGIN_ID),
			Password.from(VALID_ENCODED_PASSWORD),
			VALID_NAME,
			VALID_BIRTHDAY,
			VALID_EMAIL
		);
	}


	@Nested
	@DisplayName("생성 테스트")
	class CreateTest {

		@Test
		@DisplayName("[User.create()] 유효한 정보로 User 생성 -> 필드가 정상 설정됨")
		void createWithValidInfo() {
			// Act
			User user = createValidUser();

			// Assert
			assertAll(
				() -> assertThat(user.getLoginId().value()).isEqualTo(VALID_LOGIN_ID),
				() -> assertThat(user.getPassword().value()).isEqualTo(VALID_ENCODED_PASSWORD),
				() -> assertThat(user.getName()).isEqualTo(VALID_NAME),
				() -> assertThat(user.getBirthday()).isEqualTo(VALID_BIRTHDAY),
				() -> assertThat(user.getEmail()).isEqualTo(VALID_EMAIL)
			);
		}


		@Test
		@DisplayName("[User.create()] 이름/이메일 앞뒤 공백 -> trim 정규화")
		void createTrimsNameAndEmail() {
			// Act
			User user = User.create(
				LoginId.create(VALID_LOGIN_ID),
				Password.from(VALID_ENCODED_PASSWORD),
				"  홍길동  ",
				VALID_BIRTHDAY,
				"  test@example.com  "
			);

			// Assert
			assertAll(
				() -> assertThat(user.getName()).isEqualTo("홍길동"),
				() -> assertThat(user.getEmail()).isEqualTo("test@example.com")
			);
		}

	}


	@Nested
	@DisplayName("LoginId 검증 위임 테스트")
	class LoginIdValidationDelegationTest {

		@ParameterizedTest
		@NullAndEmptySource
		@ValueSource(strings = { "   ", "a", "invalid_id", "테스트유저" })
		@DisplayName("[LoginId.create()] 잘못된 loginId -> INVALID_LOGIN_ID_FORMAT")
		void failWhenLoginIdInvalid(String rawLoginId) {
			// Act
			CoreException exception = assertThrows(CoreException.class, () -> LoginId.create(rawLoginId));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_LOGIN_ID_FORMAT),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_LOGIN_ID_FORMAT.getMessage())
			);
		}


		@Test
		@DisplayName("[LoginId.create()] 대문자/공백 loginId -> 정규화")
		void normalizeLoginId() {
			// Act
			LoginId loginId = LoginId.create("  TestUser01  ");

			// Assert
			assertThat(loginId.value()).isEqualTo("testuser01");
		}

	}


	@Nested
	@DisplayName("이름 검증 테스트")
	class NameValidationTest {

		@ParameterizedTest
		@NullAndEmptySource
		@ValueSource(strings = { "홍길동123", "홍길동!" })
		@DisplayName("[User.create()] 잘못된 이름 -> INVALID_NAME_FORMAT")
		void failWhenNameInvalid(String name) {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> User.create(LoginId.create(VALID_LOGIN_ID), Password.from(VALID_ENCODED_PASSWORD), name, VALID_BIRTHDAY, VALID_EMAIL));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_NAME_FORMAT),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_NAME_FORMAT.getMessage())
			);
		}

	}


	@Nested
	@DisplayName("이메일 검증 테스트")
	class EmailValidationTest {

		@ParameterizedTest
		@NullAndEmptySource
		@ValueSource(strings = { "invalidemail", "test@example..com", "test@.example.com", "test@example.com." })
		@DisplayName("[User.create()] 잘못된 이메일 -> INVALID_EMAIL_FORMAT")
		void failWhenEmailInvalid(String email) {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> User.create(LoginId.create(VALID_LOGIN_ID), Password.from(VALID_ENCODED_PASSWORD), VALID_NAME, VALID_BIRTHDAY, email));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_EMAIL_FORMAT),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_EMAIL_FORMAT.getMessage())
			);
		}

	}


	@Nested
	@DisplayName("생년월일 검증 테스트")
	class BirthdayValidationTest {

		@Test
		@DisplayName("[User.create()] birthday null -> INVALID_BIRTHDAY")
		void failWhenBirthdayIsNull() {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> User.create(LoginId.create(VALID_LOGIN_ID), Password.from(VALID_ENCODED_PASSWORD), VALID_NAME, null, VALID_EMAIL));

			// Assert
			assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_BIRTHDAY);
		}


		@Test
		@DisplayName("[User.create()] birthday가 today 또는 미래 -> INVALID_BIRTHDAY")
		void failWhenBirthdayNotPast() {
			// Act
			CoreException todayException = assertThrows(CoreException.class,
				() -> User.create(
					LoginId.create(VALID_LOGIN_ID),
					Password.from(VALID_ENCODED_PASSWORD),
					VALID_NAME,
					LocalDate.now(),
					VALID_EMAIL
				));
			CoreException futureException = assertThrows(CoreException.class,
				() -> User.create(
					LoginId.create(VALID_LOGIN_ID),
					Password.from(VALID_ENCODED_PASSWORD),
					VALID_NAME,
					LocalDate.now().plusDays(1),
					VALID_EMAIL
				));

			// Assert
			assertAll(
				() -> assertThat(todayException.getErrorType()).isEqualTo(ErrorType.INVALID_BIRTHDAY),
				() -> assertThat(futureException.getErrorType()).isEqualTo(ErrorType.INVALID_BIRTHDAY)
			);
		}

	}


	@Nested
	@DisplayName("재구성/변경 테스트")
	class ReconstructAndPasswordChangeTest {

		@Test
		@DisplayName("[User.reconstruct()] 저장값으로 User 재구성")
		void reconstructFromStoredData() {
			// Act
			User user = User.reconstruct(
				1L,
				LoginId.create(VALID_LOGIN_ID),
				VALID_ENCODED_PASSWORD,
				VALID_NAME,
				VALID_BIRTHDAY,
				VALID_EMAIL
			);

			// Assert
			assertAll(
				() -> assertThat(user.getId()).isEqualTo(1L),
				() -> assertThat(user.getLoginId().value()).isEqualTo(VALID_LOGIN_ID),
				() -> assertThat(user.getPassword().value()).isEqualTo(VALID_ENCODED_PASSWORD)
			);
		}


		@Test
		@DisplayName("[User.changePassword()] 새 Password로 교체")
		void changePasswordSuccess() {
			// Arrange
			User user = createValidUser();
			Password newPassword = Password.from("newEncodedPassword");

			// Act
			user.changePassword(newPassword);

			// Assert
			assertThat(user.getPassword().value()).isEqualTo("newEncodedPassword");
		}

	}

}
