package com.loopers.queue.waitingqueue.infrastructure.auth;


import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.loopers.queue.waitingqueue.application.port.out.QueueAuthPort;
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
	@DisplayName("resolveAndRefresh()")
	class ResolveAndRefreshTest {

		@Test
		@DisplayName("[resolveAndRefresh()] 유효한 토큰 -> userId + 갱신된 토큰 반환. generateToken으로 생성한 토큰을 검증하면 원래 userId와 새 토큰을 반환")
		void resolveAndRefresh_validToken_returnsResolvedQueueToken() throws InterruptedException {
			// Arrange
			Long userId = 42L;
			String token = provider.generateToken(userId);
			Thread.sleep(10); // 만료시각 차이 발생

			// Act
			QueueAuthPort.ResolvedQueueToken result = provider.resolveAndRefresh(token);

			// Assert
			assertAll(
				() -> assertThat(result.userId()).isEqualTo(42L),
				() -> assertThat(result.refreshedToken()).isNotNull(),
				() -> assertThat(result.refreshedToken()).isNotEqualTo(token)
			);
		}


		@Test
		@DisplayName("[resolveAndRefresh()] 갱신된 토큰도 유효. 반환된 refreshedToken을 다시 resolveAndRefresh하면 동일 userId 반환")
		void resolveAndRefresh_refreshedTokenIsValid() {
			// Arrange
			Long userId = 42L;
			String token = provider.generateToken(userId);

			// Act
			QueueAuthPort.ResolvedQueueToken result = provider.resolveAndRefresh(token);
			QueueAuthPort.ResolvedQueueToken result2 = provider.resolveAndRefresh(result.refreshedToken());

			// Assert
			assertThat(result2.userId()).isEqualTo(42L);
		}


		@Test
		@DisplayName("[resolveAndRefresh()] 만료된 토큰 -> INVALID_QUEUE_TOKEN 예외. TTL이 0초인 provider로 생성한 토큰은 즉시 만료")
		void resolveAndRefresh_expiredToken_throwsException() {
			// Arrange — TTL 0초로 즉시 만료되는 provider
			QueueTokenProperties expiredProperties = new QueueTokenProperties(SECRET_KEY, 0L);
			HmacQueueAuthProvider expiredProvider = new HmacQueueAuthProvider(expiredProperties);
			String token = expiredProvider.generateToken(1L);

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> provider.resolveAndRefresh(token));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_QUEUE_TOKEN),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_QUEUE_TOKEN.getMessage())
			);
		}


		@Test
		@DisplayName("[resolveAndRefresh()] 변조된 토큰 (HMAC 불일치) -> INVALID_QUEUE_TOKEN 예외. 서명 부분을 임의로 변경하면 검증 실패")
		void resolveAndRefresh_tamperedToken_throwsException() {
			// Arrange
			String token = provider.generateToken(1L);
			String[] parts = token.split(":");
			String tamperedToken = parts[0] + ":" + parts[1] + ":tamperedhmac";

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> provider.resolveAndRefresh(tamperedToken));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_QUEUE_TOKEN),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_QUEUE_TOKEN.getMessage())
			);
		}


		@Test
		@DisplayName("[resolveAndRefresh()] userId가 변조된 토큰 -> INVALID_QUEUE_TOKEN 예외. userId 부분을 다른 값으로 변경하면 HMAC 불일치")
		void resolveAndRefresh_userIdTampered_throwsException() {
			// Arrange
			String token = provider.generateToken(1L);
			String[] parts = token.split(":");
			String tamperedToken = "999" + ":" + parts[1] + ":" + parts[2];

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> provider.resolveAndRefresh(tamperedToken));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_QUEUE_TOKEN),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_QUEUE_TOKEN.getMessage())
			);
		}


		@Test
		@DisplayName("[resolveAndRefresh()] 잘못된 형식 토큰 (파트 부족) -> INVALID_QUEUE_TOKEN 예외. 콜론 구분 3파트가 아닌 문자열")
		void resolveAndRefresh_malformedToken_throwsException() {
			// Arrange
			String malformedToken = "not-a-valid-token";

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> provider.resolveAndRefresh(malformedToken));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_QUEUE_TOKEN),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_QUEUE_TOKEN.getMessage())
			);
		}


		@Test
		@DisplayName("[resolveAndRefresh()] null 토큰 -> INVALID_QUEUE_TOKEN 예외")
		void resolveAndRefresh_nullToken_throwsException() {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> provider.resolveAndRefresh(null));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_QUEUE_TOKEN),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_QUEUE_TOKEN.getMessage())
			);
		}


		@Test
		@DisplayName("[resolveAndRefresh()] 다른 secretKey로 생성된 토큰 -> INVALID_QUEUE_TOKEN 예외. 키가 다르면 서명 검증 실패")
		void resolveAndRefresh_differentSecretKey_throwsException() {
			// Arrange — 다른 키로 생성된 provider
			QueueTokenProperties otherProperties = new QueueTokenProperties("other-secret-key-different!!", TOKEN_TTL_SECONDS);
			HmacQueueAuthProvider otherProvider = new HmacQueueAuthProvider(otherProperties);
			String token = otherProvider.generateToken(1L);

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> provider.resolveAndRefresh(token));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_QUEUE_TOKEN),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_QUEUE_TOKEN.getMessage())
			);
		}
	}


	@Nested
	@DisplayName("generateEnterReceipt()")
	class GenerateEnterReceiptTest {

		@Test
		@DisplayName("[generateEnterReceipt()] 유효한 userId -> 'enter:' 접두사와 유효한 HMAC을 포함한 영수증 반환. 콜론으로 구분된 4파트 형식 (enter:userId:timestamp:hmacHex)")
		void generateEnterReceipt_validUserId_returnsReceiptWithEnterPrefix() {
			// Arrange
			Long userId = 1L;

			// Act
			String receipt = provider.generateEnterReceipt(userId);

			// Assert
			String[] parts = receipt.split(":");
			assertAll(
				() -> assertThat(receipt).isNotNull(),
				() -> assertThat(receipt).isNotBlank(),
				() -> assertThat(parts).hasSize(4),
				() -> assertThat(parts[0]).isEqualTo("enter"),
				() -> assertThat(parts[1]).isEqualTo("1"),
				() -> assertThat(parts[2]).matches("\\d+"),
				() -> assertThat(parts[3]).isNotBlank()
			);
		}


		@Test
		@DisplayName("[generateEnterReceipt()] 동일 userId로 2회 생성 -> 타임스탬프가 달라 서로 다른 영수증 생성")
		void generateEnterReceipt_sameUserIdTwice_returnsDifferentReceipts() throws InterruptedException {
			// Arrange
			Long userId = 1L;

			// Act
			String receipt1 = provider.generateEnterReceipt(userId);
			Thread.sleep(10); // 타임스탬프 차이 발생
			String receipt2 = provider.generateEnterReceipt(userId);

			// Assert
			assertThat(receipt1).isNotEqualTo(receipt2);
		}
	}


	@Nested
	@DisplayName("resolveEnterReceipt()")
	class ResolveEnterReceiptTest {

		@Test
		@DisplayName("[resolveEnterReceipt()] generateEnterReceipt으로 생성한 영수증 -> userId 반환. 정상 발급된 영수증은 검증 통과")
		void resolveEnterReceipt_validReceipt_returnsUserId() {
			// Arrange
			String receipt = provider.generateEnterReceipt(42L);

			// Act
			Long result = provider.resolveEnterReceipt(receipt);

			// Assert
			assertThat(result).isEqualTo(42L);
		}


		@Test
		@DisplayName("[resolveEnterReceipt()] null 영수증 -> null 반환")
		void resolveEnterReceipt_nullReceipt_returnsNull() {
			// Act
			Long result = provider.resolveEnterReceipt(null);

			// Assert
			assertThat(result).isNull();
		}


		@Test
		@DisplayName("[resolveEnterReceipt()] 빈 문자열 영수증 -> null 반환")
		void resolveEnterReceipt_blankReceipt_returnsNull() {
			// Act
			Long result = provider.resolveEnterReceipt("   ");

			// Assert
			assertThat(result).isNull();
		}


		@Test
		@DisplayName("[resolveEnterReceipt()] HMAC이 변조된 영수증 -> null 반환. 서명 부분을 임의로 변경하면 검증 실패")
		void resolveEnterReceipt_tamperedHmac_returnsNull() {
			// Arrange
			String receipt = provider.generateEnterReceipt(1L);
			String[] parts = receipt.split(":");
			String tamperedReceipt = parts[0] + ":" + parts[1] + ":" + parts[2] + ":tamperedhmac";

			// Act
			Long result = provider.resolveEnterReceipt(tamperedReceipt);

			// Assert
			assertThat(result).isNull();
		}


		@Test
		@DisplayName("[resolveEnterReceipt()] 파트가 부족한 잘못된 형식 -> null 반환. 콜론 구분 4파트가 아닌 문자열")
		void resolveEnterReceipt_wrongFormat_returnsNull() {
			// Arrange
			String malformedReceipt = "enter:1:hmaconly";

			// Act
			Long result = provider.resolveEnterReceipt(malformedReceipt);

			// Assert
			assertThat(result).isNull();
		}


		@Test
		@DisplayName("[resolveEnterReceipt()] 접두사가 'enter'가 아닌 영수증 -> null 반환. 첫 번째 파트가 다르면 검증 실패")
		void resolveEnterReceipt_wrongPrefix_returnsNull() {
			// Arrange — 유효한 영수증의 접두사를 변경
			String receipt = provider.generateEnterReceipt(1L);
			String tamperedReceipt = "wrong" + receipt.substring("enter".length());

			// Act
			Long result = provider.resolveEnterReceipt(tamperedReceipt);

			// Assert
			assertThat(result).isNull();
		}


		@Test
		@DisplayName("[resolveEnterReceipt()] 만료된 영수증 -> null 반환. TTL이 0초인 provider로 생성한 영수증은 즉시 만료")
		void resolveEnterReceipt_expiredReceipt_returnsNull() {
			// Arrange — TTL 0초로 즉시 만료되는 provider
			QueueTokenProperties expiredProperties = new QueueTokenProperties(SECRET_KEY, 0L);
			HmacQueueAuthProvider expiredProvider = new HmacQueueAuthProvider(expiredProperties);
			String receipt = expiredProvider.generateEnterReceipt(1L);

			// Act
			Long result = expiredProvider.resolveEnterReceipt(receipt);

			// Assert
			assertThat(result).isNull();
		}
	}
}
