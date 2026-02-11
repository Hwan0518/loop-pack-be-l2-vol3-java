package com.loopers.user.domain.model.vo;


import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;


@DisplayName("Password 값 객체 테스트")
class PasswordTest {

	private static final LocalDate DEFAULT_BIRTHDAY = LocalDate.of(1990, 1, 15);

	@Nested
	@DisplayName("원문 비밀번호 검증 테스트")
	class ValidateRawTest {

		@Test
		@DisplayName("[Password.validateRaw()] 유효한 비밀번호 -> 예외 없이 성공")
		void validateWithValidPassword() {
			// Act & Assert
			assertDoesNotThrow(() -> Password.validateRaw("Test1234!", DEFAULT_BIRTHDAY));
		}


		@Test
		@DisplayName("[Password.validateRaw()] 8자 비밀번호(최소 유효) -> 예외 없이 성공")
		void validateWithMinimumLengthPassword() {
			// Act & Assert
			assertDoesNotThrow(() -> Password.validateRaw("Aa1!aaaa", DEFAULT_BIRTHDAY));
		}


		@Test
		@DisplayName("[Password.validateRaw()] 16자 비밀번호(최대 유효) -> 예외 없이 성공")
		void validateWithMaximumLengthPassword() {
			// Act & Assert
			assertDoesNotThrow(() -> Password.validateRaw("Aa1!aaaaaaaaaaaa", DEFAULT_BIRTHDAY));
		}


		@ParameterizedTest
		@ValueSource(strings = { "Test12!", "Test1!" })
		@DisplayName("[Password.validateRaw()] 8자 미만 비밀번호 -> CoreException(ErrorType.INVALID_PASSWORD_FORMAT) 발생. "
			+ "에러 메시지: '비밀번호는 8~16자이며, 영문 대소문자, 숫자, 특수문자를 모두 포함해야 합니다.'")
		void failWhenPasswordLessThan8Characters(String rawPassword) {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> Password.validateRaw(rawPassword, DEFAULT_BIRTHDAY));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_PASSWORD_FORMAT),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_PASSWORD_FORMAT.getMessage())
			);
		}


		@Test
		@DisplayName("[Password.validateRaw()] 16자 초과 비밀번호 -> CoreException(ErrorType.INVALID_PASSWORD_FORMAT) 발생. "
			+ "에러 메시지: '비밀번호는 8~16자이며, 영문 대소문자, 숫자, 특수문자를 모두 포함해야 합니다.'")
		void failWhenPasswordMoreThan16Characters() {
			// Arrange
			String rawPassword = "Test1234567890!@#";

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> Password.validateRaw(rawPassword, DEFAULT_BIRTHDAY));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_PASSWORD_FORMAT),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_PASSWORD_FORMAT.getMessage())
			);
		}


		@ParameterizedTest
		@ValueSource(strings = { "test1234!", "TEST1234!", "Testtest!", "Test12345", "Test!@#$%" })
		@DisplayName("[Password.validateRaw()] 영문 대소문자/숫자/특수문자 누락 -> CoreException(ErrorType.INVALID_PASSWORD_FORMAT) 발생. "
			+ "에러 메시지: '비밀번호는 8~16자이며, 영문 대소문자, 숫자, 특수문자를 모두 포함해야 합니다.'")
		void failWhenMissingRequiredCharacters(String rawPassword) {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> Password.validateRaw(rawPassword, DEFAULT_BIRTHDAY));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_PASSWORD_FORMAT),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_PASSWORD_FORMAT.getMessage())
			);
		}


		@Test
		@DisplayName("[Password.validateRaw()] 생년월일(YYYYMMDD) 포함 -> CoreException(ErrorType.PASSWORD_CONTAINS_BIRTHDAY) 발생. "
			+ "에러 메시지: '비밀번호에 생년월일을 포함할 수 없습니다.'")
		void failWhenContainsBirthdayYYYYMMDD() {
			// Arrange
			String rawPassword = "Aa19900115!";

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> Password.validateRaw(rawPassword, DEFAULT_BIRTHDAY));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.PASSWORD_CONTAINS_BIRTHDAY),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.PASSWORD_CONTAINS_BIRTHDAY.getMessage())
			);
		}


		@Test
		@DisplayName("[Password.validateRaw()] 생년월일(YYMMDD) 포함 -> CoreException(ErrorType.PASSWORD_CONTAINS_BIRTHDAY) 발생. "
			+ "에러 메시지: '비밀번호에 생년월일을 포함할 수 없습니다.'")
		void failWhenContainsBirthdayYYMMDD() {
			// Arrange
			String rawPassword = "Aa900115!@";

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> Password.validateRaw(rawPassword, DEFAULT_BIRTHDAY));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.PASSWORD_CONTAINS_BIRTHDAY),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.PASSWORD_CONTAINS_BIRTHDAY.getMessage())
			);
		}


		@ParameterizedTest
		@ValueSource(strings = { "Test 1234!", "Test\t1234!", "Test\n1234!" })
		@DisplayName("[Password.validateRaw()] 비밀번호에 공백/탭/개행 포함 -> CoreException(ErrorType.INVALID_PASSWORD_FORMAT) 발생. "
			+ "허용 문자 외 공백 문자 차단")
		void failWhenPasswordContainsWhitespace(String rawPassword) {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> Password.validateRaw(rawPassword, DEFAULT_BIRTHDAY));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_PASSWORD_FORMAT),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_PASSWORD_FORMAT.getMessage())
			);
		}


		@ParameterizedTest
		@ValueSource(strings = { "Test1234!한글", "Tëst1234!" })
		@DisplayName("[Password.validateRaw()] 비밀번호에 비ASCII 문자 포함 -> CoreException(ErrorType.INVALID_PASSWORD_FORMAT) 발생. "
			+ "허용 문자 외 한글/특수 유니코드 차단")
		void failWhenPasswordContainsNonAscii(String rawPassword) {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> Password.validateRaw(rawPassword, DEFAULT_BIRTHDAY));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_PASSWORD_FORMAT),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_PASSWORD_FORMAT.getMessage())
			);
		}


		@Test
		@DisplayName("[Password.validateRaw()] 생년월일(YYYY-MM-DD) 포함 -> CoreException(ErrorType.PASSWORD_CONTAINS_BIRTHDAY) 발생. "
			+ "에러 메시지: '비밀번호에 생년월일을 포함할 수 없습니다.'")
		void failWhenContainsBirthdayWithDashes() {
			// Arrange
			String rawPassword = "Aa1990-01-15!";

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> Password.validateRaw(rawPassword, DEFAULT_BIRTHDAY));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.PASSWORD_CONTAINS_BIRTHDAY),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.PASSWORD_CONTAINS_BIRTHDAY.getMessage())
			);
		}


		@Test
		@DisplayName("[Password.validateRaw()] 비밀번호가 null -> CoreException(ErrorType.INVALID_PASSWORD_FORMAT) 발생. "
			+ "에러 메시지: '비밀번호는 8~16자이며, 영문 대소문자, 숫자, 특수문자를 모두 포함해야 합니다.'")
		void failWhenPasswordIsNull() {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> Password.validateRaw(null, DEFAULT_BIRTHDAY));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_PASSWORD_FORMAT),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_PASSWORD_FORMAT.getMessage())
			);
		}

	}

	@Nested
	@DisplayName("fromEncoded 테스트")
	class FromEncodedTest {

		@Test
		@DisplayName("[Password.fromEncoded()] 인코딩된 비밀번호로 Password 객체 생성 -> value가 동일한 Password 반환")
		void createFromEncodedPassword() {
			// Arrange
			String encodedValue = "encodedPasswordValue";

			// Act
			Password restored = Password.from(encodedValue);

			// Assert
			assertThat(restored.value()).isEqualTo(encodedValue);
		}

	}

}
