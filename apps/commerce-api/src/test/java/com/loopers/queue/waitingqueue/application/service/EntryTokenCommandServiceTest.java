package com.loopers.queue.waitingqueue.application.service;


import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
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
			String entryToken = "entry-test-token-1";
			given(waitingQueueRedisPort.getEntryToken(userId)).willReturn("entry-test-token-1");

			// Act & Assert
			assertDoesNotThrow(() -> service.validateEntryToken(userId, entryToken));
		}


		@Test
		@DisplayName("[validateEntryToken()] 불일치 토큰 -> INVALID_QUEUE_TOKEN 예외. Redis 토큰과 다름")
		void validateEntryToken_mismatchToken_throwsException() {
			// Arrange
			Long userId = 1L;
			given(waitingQueueRedisPort.getEntryToken(userId)).willReturn("entry-test-token-1");

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
	@DisplayName("validateAndLock()")
	class ValidateAndLockTest {

		@Test
		@DisplayName("[validateAndLock()] 유효한 토큰 + 락 획득 성공 -> 예외 없음. 토큰 검증 후 acquireOrderLock 호출")
		void validateAndLock_validTokenAndLockAcquired_noException() {
			// Arrange
			Long userId = 1L;
			String entryToken = "entry-test-token-1";
			given(waitingQueueRedisPort.getEntryToken(userId)).willReturn("entry-test-token-1");
			given(waitingQueueRedisPort.acquireOrderLock(userId, 300L)).willReturn(true);

			// Act & Assert
			assertDoesNotThrow(() -> service.validateAndLock(userId, entryToken));
			verify(waitingQueueRedisPort).acquireOrderLock(userId, 300L);
		}


		@Test
		@DisplayName("[validateAndLock()] 유효한 토큰 + 락 획득 실패 -> ORDER_ALREADY_IN_PROGRESS 예외. 이미 주문 처리 중")
		void validateAndLock_validTokenButLockNotAcquired_throwsOrderAlreadyInProgress() {
			// Arrange
			Long userId = 1L;
			String entryToken = "entry-test-token-1";
			given(waitingQueueRedisPort.getEntryToken(userId)).willReturn("entry-test-token-1");
			given(waitingQueueRedisPort.acquireOrderLock(userId, 300L)).willReturn(false);

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> service.validateAndLock(userId, entryToken));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.ORDER_ALREADY_IN_PROGRESS),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.ORDER_ALREADY_IN_PROGRESS.getMessage())
			);
		}


		@Test
		@DisplayName("[validateAndLock()] 유효하지 않은 토큰 -> INVALID_QUEUE_TOKEN 예외. acquireOrderLock 호출되지 않음")
		void validateAndLock_invalidToken_throwsInvalidQueueToken() {
			// Arrange
			Long userId = 1L;
			given(waitingQueueRedisPort.getEntryToken(userId)).willReturn("entry-test-token-1");

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> service.validateAndLock(userId, "wrong-token"));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_QUEUE_TOKEN),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_QUEUE_TOKEN.getMessage())
			);
			verify(waitingQueueRedisPort, never()).acquireOrderLock(userId, 300L);
		}
	}


	@Nested
	@DisplayName("completeOrder() + releaseOrderLock()")
	class CompleteOrderAndReleaseOrderLockTest {

		@Test
		@DisplayName("[completeOrder()] 주문 완료 후 -> deleteEntryToken과 releaseOrderLock 모두 호출. best-effort 정리")
		void completeOrder_callsDeleteEntryTokenAndReleaseOrderLock() {
			// Arrange
			Long userId = 1L;

			// Act
			service.completeOrder(userId);

			// Assert
			assertAll(
				() -> verify(waitingQueueRedisPort).deleteEntryToken(userId),
				() -> verify(waitingQueueRedisPort).releaseOrderLock(userId)
			);
		}


		@Test
		@DisplayName("[completeOrder()] deleteEntryToken 예외 -> 예외 전파 없음, releaseOrderLock 미호출. 토큰 삭제 불확실 시 락 유지")
		void completeOrder_deleteFails_keepsLockAndSwallowsException() {
			// Arrange
			Long userId = 2L;
			willThrow(new RuntimeException("redis delete failed"))
				.given(waitingQueueRedisPort).deleteEntryToken(userId);

			// Act & Assert
			assertDoesNotThrow(() -> service.completeOrder(userId));
			verify(waitingQueueRedisPort).deleteEntryToken(userId);
			verify(waitingQueueRedisPort, never()).releaseOrderLock(userId);
		}


		@Test
		@DisplayName("[completeOrder()] releaseOrderLock 예외 -> 예외 전파 없음. 토큰 삭제 성공 후 락 해제 실패는 TTL에 위임")
		void completeOrder_releaseFails_swallowsException() {
			// Arrange
			Long userId = 3L;
			willThrow(new RuntimeException("redis release failed"))
				.given(waitingQueueRedisPort).releaseOrderLock(userId);

			// Act & Assert
			assertDoesNotThrow(() -> service.completeOrder(userId));
			assertAll(
				() -> verify(waitingQueueRedisPort).deleteEntryToken(userId),
				() -> verify(waitingQueueRedisPort).releaseOrderLock(userId)
			);
		}


		@Test
		@DisplayName("[releaseOrderLock()] 주문 실패 후 -> releaseOrderLock만 호출. 토큰은 보존하여 재시도 가능")
		void releaseOrderLock_callsOnlyReleaseOrderLock() {
			// Arrange
			Long userId = 1L;

			// Act
			service.releaseOrderLock(userId);

			// Assert
			assertAll(
				() -> verify(waitingQueueRedisPort).releaseOrderLock(userId),
				() -> verify(waitingQueueRedisPort, never()).deleteEntryToken(userId)
			);
		}
	}
}
