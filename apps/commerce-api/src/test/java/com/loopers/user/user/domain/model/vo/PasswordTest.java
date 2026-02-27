package com.loopers.user.user.domain.model.vo;


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

	private static final LocalDate DEFAULT_BIRTH_DATE = LocalDate.of(1990, 1, 15);

	@Nested
	@DisplayName("비밀번호 정책 검증 테스트")
	class ValidatePasswordPolicyTest {

		@Test
		@DisplayName("[Password.validatePasswordPolicy()] 유효한 비밀번호 -> 예외 없이 통과")
		void validateWithValidPassword() {
			// Arrange
			String rawPassword = "Test1234!";

			// Act & Assert
			assertDoesNotThrow(() -> Password.validatePasswordPolicy(rawPassword, DEFAULT_BIRTH_DATE));
		}


		@Test
		@DisplayName("[Password.validatePasswordPolicy()] 8자 비밀번호(최소 유효) -> 예외 없이 통과")
		void validateWithMinimumLengthPassword() {
			// Arrange
			String rawPassword = "Aa1!aaaa";

			// Act & Assert
			assertDoesNotThrow(() -> Password.validatePasswordPolicy(rawPassword, DEFAULT_BIRTH_DATE));
		}


		@Test
		@DisplayName("[Password.validatePasswordPolicy()] 16자 비밀번호(최대 유효) -> 예외 없이 통과")
		void validateWithMaximumLengthPassword() {
			// Arrange
			String rawPassword = "Aa1!aaaaaaaaaaaa";

			// Act & Assert
			assertDoesNotThrow(() -> Password.validatePasswordPolicy(rawPassword, DEFAULT_BIRTH_DATE));
		}


		@ParameterizedTest
		@ValueSource(strings = { "Test12!", "Test1!" })
		@DisplayName("[Password.validatePasswordPolicy()] 8자 미만 비밀번호 -> CoreException(ErrorType.INVALID_PASSWORD_FORMAT) 발생. "
			+ "에러 메시지: '비밀번호는 8~16자이며, 영문 대소문자, 숫자, 특수문자를 모두 포함해야 합니다.'")
		void failWhenPasswordLessThan8Characters(String rawPassword) {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> Password.validatePasswordPolicy(rawPassword, DEFAULT_BIRTH_DATE));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_PASSWORD_FORMAT),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_PASSWORD_FORMAT.getMessage())
			);
		}


		@Test
		@DisplayName("[Password.validatePasswordPolicy()] 16자 초과 비밀번호 -> CoreException(ErrorType.INVALID_PASSWORD_FORMAT) 발생. "
			+ "에러 메시지: '비밀번호는 8~16자이며, 영문 대소문자, 숫자, 특수문자를 모두 포함해야 합니다.'")
		void failWhenPasswordMoreThan16Characters() {
			// Arrange
			String rawPassword = "Test1234567890!@#";

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> Password.validatePasswordPolicy(rawPassword, DEFAULT_BIRTH_DATE));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_PASSWORD_FORMAT),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_PASSWORD_FORMAT.getMessage())
			);
		}


		@ParameterizedTest
		@ValueSource(strings = { "test1234!", "TEST1234!", "Testtest!", "Test12345", "Test!@#$%" })
		@DisplayName("[Password.validatePasswordPolicy()] 영문 대소문자/숫자/특수문자 누락 -> CoreException(ErrorType.INVALID_PASSWORD_FORMAT) 발생. "
			+ "에러 메시지: '비밀번호는 8~16자이며, 영문 대소문자, 숫자, 특수문자를 모두 포함해야 합니다.'")
		void failWhenMissingRequiredCharacters(String rawPassword) {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> Password.validatePasswordPolicy(rawPassword, DEFAULT_BIRTH_DATE));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_PASSWORD_FORMAT),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_PASSWORD_FORMAT.getMessage())
			);
		}


		@Test
		@DisplayName("[Password.validatePasswordPolicy()] 생년월일(YYYYMMDD) 포함 -> CoreException(ErrorType.PASSWORD_CONTAINS_BIRTH_DATE) 발생. "
			+ "에러 메시지: '비밀번호에 생년월일을 포함할 수 없습니다.'")
		void failWhenContainsBirthDateYYYYMMDD() {
			// Arrange
			String rawPassword = "Aa19900115!";

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> Password.validatePasswordPolicy(rawPassword, DEFAULT_BIRTH_DATE));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.PASSWORD_CONTAINS_BIRTH_DATE),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.PASSWORD_CONTAINS_BIRTH_DATE.getMessage())
			);
		}


		@Test
		@DisplayName("[Password.validatePasswordPolicy()] 생년월일(YYMMDD) 포함 -> CoreException(ErrorType.PASSWORD_CONTAINS_BIRTH_DATE) 발생. "
			+ "에러 메시지: '비밀번호에 생년월일을 포함할 수 없습니다.'")
		void failWhenContainsBirthDateYYMMDD() {
			// Arrange
			String rawPassword = "Aa900115!@";

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> Password.validatePasswordPolicy(rawPassword, DEFAULT_BIRTH_DATE));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.PASSWORD_CONTAINS_BIRTH_DATE),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.PASSWORD_CONTAINS_BIRTH_DATE.getMessage())
			);
		}


		@ParameterizedTest
		@ValueSource(strings = { "Test 1234!", "Test\t1234!", "Test\n1234!" })
		@DisplayName("[Password.validatePasswordPolicy()] 비밀번호에 공백/탭/개행 포함 -> CoreException(ErrorType.INVALID_PASSWORD_FORMAT) 발생. "
			+ "허용 문자 외 공백 문자 차단")
		void failWhenPasswordContainsWhitespace(String rawPassword) {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> Password.validatePasswordPolicy(rawPassword, DEFAULT_BIRTH_DATE));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_PASSWORD_FORMAT),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_PASSWORD_FORMAT.getMessage())
			);
		}


		@ParameterizedTest
		@ValueSource(strings = { "Test1234!한글", "Tëst1234!" })
		@DisplayName("[Password.validatePasswordPolicy()] 비밀번호에 비ASCII 문자 포함 -> CoreException(ErrorType.INVALID_PASSWORD_FORMAT) 발생. "
			+ "허용 문자 외 한글/특수 유니코드 차단")
		void failWhenPasswordContainsNonAscii(String rawPassword) {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> Password.validatePasswordPolicy(rawPassword, DEFAULT_BIRTH_DATE));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_PASSWORD_FORMAT),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_PASSWORD_FORMAT.getMessage())
			);
		}


		@Test
		@DisplayName("[Password.validatePasswordPolicy()] 생년월일(YYYY-MM-DD) 포함 -> CoreException(ErrorType.PASSWORD_CONTAINS_BIRTH_DATE) 발생. "
			+ "에러 메시지: '비밀번호에 생년월일을 포함할 수 없습니다.'")
		void failWhenContainsBirthDateWithDashes() {
			// Arrange
			String rawPassword = "Aa1990-01-15!";

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> Password.validatePasswordPolicy(rawPassword, DEFAULT_BIRTH_DATE));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.PASSWORD_CONTAINS_BIRTH_DATE),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.PASSWORD_CONTAINS_BIRTH_DATE.getMessage())
			);
		}


		@Test
		@DisplayName("[Password.validatePasswordPolicy()] 비밀번호가 null -> CoreException(ErrorType.INVALID_PASSWORD_FORMAT) 발생")
		void failWhenPasswordIsNull() {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> Password.validatePasswordPolicy(null, DEFAULT_BIRTH_DATE));

			// Assert
			assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_PASSWORD_FORMAT);
		}

	}

	@Nested
	@DisplayName("from 테스트")
	class FromTest {

		@Test
		@DisplayName("[Password.from()] 인코딩된 비밀번호로 Password 객체 생성 -> value가 그대로 유지됨")
		void createFromEncodedPassword() {
			// Arrange
			String encodedValue = "encodedPasswordHash";

			// Act
			Password password = Password.from(encodedValue);

			// Assert
			assertThat(password.value()).isEqualTo(encodedValue);
		}

	}

}
