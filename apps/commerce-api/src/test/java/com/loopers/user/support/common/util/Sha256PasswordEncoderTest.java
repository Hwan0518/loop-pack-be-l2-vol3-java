package com.loopers.user.support.common.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@DisplayName("Sha256PasswordEncoder 테스트")
class Sha256PasswordEncoderTest {

	private Sha256PasswordEncoder encoder;

	@BeforeEach
	void setUp() {
		encoder = new Sha256PasswordEncoder();
	}

	@Nested
	@DisplayName("encode 테스트")
	class EncodeTest {

		@Test
		@DisplayName("[encode()] 동일한 원문 비밀번호 -> 항상 동일한 인코딩 결과 반환. 결정적(deterministic) 인코딩")
		void encodeDeterministic() {
			// Arrange
			String rawPassword = "Test1234!";

			// Act
			String first = encoder.encode(rawPassword);
			String second = encoder.encode(rawPassword);

			// Assert
			assertThat(first).isEqualTo(second);
		}

		@Test
		@DisplayName("[encode()] 원문 비밀번호 인코딩 -> 원문과 다른 값 반환. SHA-256 + Base64 인코딩 적용")
		void encodeReturnsDifferentFromRaw() {
			// Arrange
			String rawPassword = "Test1234!";

			// Act
			String encoded = encoder.encode(rawPassword);

			// Assert
			assertAll(
				() -> assertThat(encoded).isNotNull(),
				() -> assertThat(encoded).isNotEqualTo(rawPassword)
			);
		}

		@Test
		@DisplayName("[encode()] 서로 다른 비밀번호 -> 서로 다른 인코딩 결과 반환")
		void encodeDifferentPasswordsProduceDifferentResults() {
			// Arrange
			String password1 = "Test1234!";
			String password2 = "Other5678@";

			// Act
			String encoded1 = encoder.encode(password1);
			String encoded2 = encoder.encode(password2);

			// Assert
			assertThat(encoded1).isNotEqualTo(encoded2);
		}
	}

	@Nested
	@DisplayName("matches 테스트")
	class MatchesTest {

		@Test
		@DisplayName("[matches()] 원문 비밀번호와 인코딩된 비밀번호 매칭 -> true 반환")
		void matchesWithCorrectPassword() {
			// Arrange
			String rawPassword = "Test1234!";
			String encoded = encoder.encode(rawPassword);

			// Act
			boolean result = encoder.matches(rawPassword, encoded);

			// Assert
			assertThat(result).isTrue();
		}

		@Test
		@DisplayName("[matches()] 다른 원문 비밀번호와 인코딩된 비밀번호 매칭 -> false 반환")
		void matchesWithWrongPassword() {
			// Arrange
			String rawPassword = "Test1234!";
			String encoded = encoder.encode(rawPassword);

			// Act
			boolean result = encoder.matches("Wrong1234!", encoded);

			// Assert
			assertThat(result).isFalse();
		}

		@Test
		@DisplayName("[matches()] rawPassword가 null -> false 반환")
		void matchesWithNullRawPassword() {
			// Arrange
			String encoded = encoder.encode("Test1234!");

			// Act
			boolean result = encoder.matches(null, encoded);

			// Assert
			assertThat(result).isFalse();
		}

		@Test
		@DisplayName("[matches()] encodedPassword가 null -> false 반환")
		void matchesWithNullEncodedPassword() {
			// Act
			boolean result = encoder.matches("Test1234!", null);

			// Assert
			assertThat(result).isFalse();
		}
	}
}
