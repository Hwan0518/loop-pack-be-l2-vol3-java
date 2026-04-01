package com.loopers.queue.waitingqueue.infrastructure.auth;


import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.loopers.queue.waitingqueue.support.config.QueueTokenProperties;
import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;


class HmacQueueAuthProviderTest {

	private HmacQueueAuthProvider provider;

	private static final String SECRET_KEY = "test-secret-key-for-hmac-signing-32bytes!";
	private static final long TOKEN_TTL_SECONDS = 300L;


	@BeforeEach
	void setUp() {
		QueueTokenProperties properties = new QueueTokenProperties(SECRET_KEY, TOKEN_TTL_SECONDS);
		provider = new HmacQueueAuthProvider(properties);
	}


	@Nested
	@DisplayName("generateToken()")
	class GenerateTokenTest {

		@Test
		@DisplayName("[generateToken()] 유효한 userId -> 토큰 문자열 반환. 콜론으로 구분된 3파트 형식 (userId:expiryEpochMs:hmacHex)")
		void generateToken_validUserId_returnsTokenString() {
			// Arrange
			Long userId = 1L;

			// Act
			String token = provider.generateToken(userId);

			// Assert
			assertAll(
				() -> assertThat(token).isNotNull(),
				() -> assertThat(token).isNotBlank(),
				() -> assertThat(token.split(":")).hasSize(3),
				() -> assertThat(token.split(":")[0]).isEqualTo("1")
			);
		}


		@Test
		@DisplayName("[generateToken()] 동일 userId로 2회 생성 -> 만료시각이 달라 서로 다른 토큰 생성")
		void generateToken_sameuserIdTwice_returnsDifferentTokens() throws InterruptedException {
			// Arrange
			Long userId = 1L;

			// Act
			String token1 = provider.generateToken(userId);
			Thread.sleep(10); // 만료시각 차이 발생
			String token2 = provider.generateToken(userId);

			// Assert
			assertThat(token1).isNotEqualTo(token2);
		}
	}


	@Nested
	@DisplayName("resolveUserId()")
	class ResolveUserIdTest {

		@Test
		@DisplayName("[resolveUserId()] 유효한 토큰 -> userId 반환. generateToken으로 생성한 토큰을 검증하면 원래 userId를 반환")
		void resolveUserId_validToken_returnsUserId() {
			// Arrange
			Long userId = 42L;
			String token = provider.generateToken(userId);

			// Act
			Long resolvedUserId = provider.resolveUserId(token);

			// Assert
			assertThat(resolvedUserId).isEqualTo(42L);
		}


		@Test
		@DisplayName("[resolveUserId()] 만료된 토큰 -> INVALID_QUEUE_TOKEN 예외. TTL이 0초인 provider로 생성한 토큰은 즉시 만료")
		void resolveUserId_expiredToken_throwsException() {
			// Arrange — TTL 0초로 즉시 만료되는 provider
			QueueTokenProperties expiredProperties = new QueueTokenProperties(SECRET_KEY, 0L);
			HmacQueueAuthProvider expiredProvider = new HmacQueueAuthProvider(expiredProperties);
			String token = expiredProvider.generateToken(1L);

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> provider.resolveUserId(token));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_QUEUE_TOKEN),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_QUEUE_TOKEN.getMessage())
			);
		}


		@Test
		@DisplayName("[resolveUserId()] 변조된 토큰 (HMAC 불일치) -> INVALID_QUEUE_TOKEN 예외. 서명 부분을 임의로 변경하면 검증 실패")
		void resolveUserId_tamperedToken_throwsException() {
			// Arrange
			String token = provider.generateToken(1L);
			String[] parts = token.split(":");
			String tamperedToken = parts[0] + ":" + parts[1] + ":tamperedhmac";

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> provider.resolveUserId(tamperedToken));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_QUEUE_TOKEN),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_QUEUE_TOKEN.getMessage())
			);
		}


		@Test
		@DisplayName("[resolveUserId()] userId가 변조된 토큰 -> INVALID_QUEUE_TOKEN 예외. userId 부분을 다른 값으로 변경하면 HMAC 불일치")
		void resolveUserId_userIdTampered_throwsException() {
			// Arrange
			String token = provider.generateToken(1L);
			String[] parts = token.split(":");
			String tamperedToken = "999" + ":" + parts[1] + ":" + parts[2];

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> provider.resolveUserId(tamperedToken));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_QUEUE_TOKEN),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_QUEUE_TOKEN.getMessage())
			);
		}


		@Test
		@DisplayName("[resolveUserId()] 잘못된 형식 토큰 (파트 부족) -> INVALID_QUEUE_TOKEN 예외. 콜론 구분 3파트가 아닌 문자열")
		void resolveUserId_malformedToken_throwsException() {
			// Arrange
			String malformedToken = "not-a-valid-token";

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> provider.resolveUserId(malformedToken));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_QUEUE_TOKEN),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_QUEUE_TOKEN.getMessage())
			);
		}


		@Test
		@DisplayName("[resolveUserId()] null 토큰 -> INVALID_QUEUE_TOKEN 예외")
		void resolveUserId_nullToken_throwsException() {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> provider.resolveUserId(null));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_QUEUE_TOKEN),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_QUEUE_TOKEN.getMessage())
			);
		}


		@Test
		@DisplayName("[resolveUserId()] 다른 secretKey로 생성된 토큰 -> INVALID_QUEUE_TOKEN 예외. 키가 다르면 서명 검증 실패")
		void resolveUserId_differentSecretKey_throwsException() {
			// Arrange — 다른 키로 생성된 provider
			QueueTokenProperties otherProperties = new QueueTokenProperties("other-secret-key-different!!", TOKEN_TTL_SECONDS);
			HmacQueueAuthProvider otherProvider = new HmacQueueAuthProvider(otherProperties);
			String token = otherProvider.generateToken(1L);

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> provider.resolveUserId(token));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_QUEUE_TOKEN),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_QUEUE_TOKEN.getMessage())
			);
		}
	}


	@Nested
	@DisplayName("refreshToken()")
	class RefreshTokenTest {

		@Test
		@DisplayName("[refreshToken()] 유효한 토큰 -> 새 토큰 반환. 같은 userId지만 만료시각이 갱신된 새 토큰")
		void refreshToken_validToken_returnsNewToken() throws InterruptedException {
			// Arrange
			Long userId = 1L;
			String originalToken = provider.generateToken(userId);
			Thread.sleep(10); // 만료시각 차이 발생

			// Act
			String refreshedToken = provider.refreshToken(originalToken);

			// Assert
			assertAll(
				() -> assertThat(refreshedToken).isNotNull(),
				() -> assertThat(refreshedToken).isNotEqualTo(originalToken),
				() -> assertThat(provider.resolveUserId(refreshedToken)).isEqualTo(userId)
			);
		}


		@Test
		@DisplayName("[refreshToken()] 만료된 토큰 -> INVALID_QUEUE_TOKEN 예외. 만료된 토큰은 갱신 불가")
		void refreshToken_expiredToken_throwsException() {
			// Arrange
			QueueTokenProperties expiredProperties = new QueueTokenProperties(SECRET_KEY, 0L);
			HmacQueueAuthProvider expiredProvider = new HmacQueueAuthProvider(expiredProperties);
			String expiredToken = expiredProvider.generateToken(1L);

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> provider.refreshToken(expiredToken));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_QUEUE_TOKEN),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_QUEUE_TOKEN.getMessage())
			);
		}
	}
}
