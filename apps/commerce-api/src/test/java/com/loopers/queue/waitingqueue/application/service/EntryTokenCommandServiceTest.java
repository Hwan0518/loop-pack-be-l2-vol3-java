package com.loopers.queue.waitingqueue.application.service;


import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.loopers.queue.waitingqueue.application.port.out.WaitingQueueRedisPort;
import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


@ExtendWith(MockitoExtension.class)
class EntryTokenCommandServiceTest {

	private EntryTokenCommandService service;

	@Mock
	private WaitingQueueRedisPort waitingQueueRedisPort;


	@BeforeEach
	void setUp() {
		service = new EntryTokenCommandService(waitingQueueRedisPort);
	}


	@Nested
	@DisplayName("validateEntryToken()")
	class ValidateEntryTokenTest {

		@Test
		@DisplayName("[validateEntryToken()] 유효한 토큰 -> 예외 없음. Redis에 저장된 토큰과 일치")
		void validateEntryToken_validToken_noException() {
			// Arrange
			Long userId = 1L;
			String entryToken = "entry-token-1";
			given(waitingQueueRedisPort.getEntryToken(userId)).willReturn("entry-token-1");

			// Act & Assert
			assertDoesNotThrow(() -> service.validateEntryToken(userId, entryToken));
		}


		@Test
		@DisplayName("[validateEntryToken()] 불일치 토큰 -> INVALID_QUEUE_TOKEN 예외. Redis 토큰과 다름")
		void validateEntryToken_mismatchToken_throwsException() {
			// Arrange
			Long userId = 1L;
			given(waitingQueueRedisPort.getEntryToken(userId)).willReturn("entry-token-1");

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> service.validateEntryToken(userId, "wrong-token"));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_QUEUE_TOKEN),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_QUEUE_TOKEN.getMessage())
			);
		}


		@Test
		@DisplayName("[validateEntryToken()] 토큰 없음 (만료/미발급) -> INVALID_QUEUE_TOKEN 예외. Redis에 키가 없음")
		void validateEntryToken_noToken_throwsException() {
			// Arrange
			Long userId = 1L;
			given(waitingQueueRedisPort.getEntryToken(userId)).willReturn(null);

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> service.validateEntryToken(userId, "some-token"));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_QUEUE_TOKEN),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_QUEUE_TOKEN.getMessage())
			);
		}
	}


	@Nested
	@DisplayName("deleteEntryToken()")
	class DeleteEntryTokenTest {

		@Test
		@DisplayName("[deleteEntryToken()] 주문 완료 후 -> Redis entry-token 삭제 호출")
		void deleteEntryToken_afterOrder_deletesToken() {
			// Arrange
			Long userId = 1L;

			// Act
			service.deleteEntryToken(userId);

			// Assert
			verify(waitingQueueRedisPort).deleteEntryToken(userId);
		}
	}
}
