package com.loopers.queue.waitingqueue.application.service;


import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

import com.loopers.queue.waitingqueue.application.dto.out.QueuePositionOutDto;
import com.loopers.queue.waitingqueue.application.port.out.QueueAuthPort;
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
class WaitingQueueQueryServiceTest {

	private WaitingQueueQueryService service;

	@Mock
	private WaitingQueueRedisPort waitingQueueRedisPort;

	@Mock
	private QueueAuthPort queueAuthPort;

	private static final String QUEUE_TOKEN = "valid-queue-token";
	private static final String REFRESHED_TOKEN = "refreshed-token";
	private static final String ENTER_RECEIPT = "enter:1:1711900000000:hmac-hex";


	@BeforeEach
	void setUp() {
		service = new WaitingQueueQueryService(waitingQueueRedisPort, queueAuthPort);
	}


	@Nested
	@DisplayName("getPosition()")
	class GetPositionTest {

		@Test
		@DisplayName("[getPosition()] 대기열에 존재 (rank=0) -> status=WAITING, position=1. 1-based 순번으로 변환")
		void getPosition_inQueue_returnsPosition() {
			// Arrange
			Long userId = 1L;
			given(waitingQueueRedisPort.getPosition(userId)).willReturn(0L);
			given(waitingQueueRedisPort.getQueueSize()).willReturn(100L);
			given(queueAuthPort.resolveAndRefresh(QUEUE_TOKEN))
				.willReturn(new QueueAuthPort.ResolvedQueueToken(userId, REFRESHED_TOKEN));

			// Act
			QueuePositionOutDto result = service.getPosition(QUEUE_TOKEN, ENTER_RECEIPT);

			// Assert
			assertAll(
				() -> assertThat(result.status()).isEqualTo("WAITING"),
				() -> assertThat(result.position()).isEqualTo(1),
				() -> assertThat(result.estimatedWaitSeconds()).isGreaterThan(0),
				() -> assertThat(result.retryAfterMs()).isGreaterThan(0),
				() -> assertThat(result.refreshedToken()).isEqualTo(REFRESHED_TOKEN),
				() -> assertThat(result.entryToken()).isNull()
			);
		}


		@Test
		@DisplayName("[getPosition()] 대기열에 없고 입장 토큰 존재 -> status=TOKEN_ISSUED + entryToken 반환")
		void getPosition_tokenIssued_returnsToken() {
			// Arrange
			Long userId = 1L;
			given(waitingQueueRedisPort.getPosition(userId)).willReturn(null);
			given(waitingQueueRedisPort.getEntryToken(userId)).willReturn("entry-test-token-value");
			given(queueAuthPort.resolveAndRefresh(QUEUE_TOKEN))
				.willReturn(new QueueAuthPort.ResolvedQueueToken(userId, REFRESHED_TOKEN));

			// Act
			QueuePositionOutDto result = service.getPosition(QUEUE_TOKEN, ENTER_RECEIPT);

			// Assert
			assertAll(
				() -> assertThat(result.status()).isEqualTo("TOKEN_ISSUED"),
				() -> assertThat(result.position()).isEqualTo(0),
				() -> assertThat(result.entryToken()).isEqualTo("entry-test-token-value"),
				() -> assertThat(result.refreshedToken()).isEqualTo(REFRESHED_TOKEN)
			);
		}


		@Test
		@DisplayName("[getPosition()] 유효한 진입 증명 있음 + 동일 userId + Redis 미반영 -> status=PROCESSING. Consumer 처리 대기 중")
		void getPosition_validReceipt_returnsProcessing() {
			// Arrange
			Long userId = 1L;
			given(waitingQueueRedisPort.getPosition(userId)).willReturn(null);
			given(waitingQueueRedisPort.getEntryToken(userId)).willReturn(null);
			given(waitingQueueRedisPort.isRejected(userId)).willReturn(false);
			given(queueAuthPort.resolveAndRefresh(QUEUE_TOKEN))
				.willReturn(new QueueAuthPort.ResolvedQueueToken(userId, REFRESHED_TOKEN));
			given(queueAuthPort.resolveEnterReceipt(ENTER_RECEIPT)).willReturn(1L);

			// Act
			QueuePositionOutDto result = service.getPosition(QUEUE_TOKEN, ENTER_RECEIPT);

			// Assert
			assertAll(
				() -> assertThat(result.status()).isEqualTo("PROCESSING"),
				() -> assertThat(result.position()).isEqualTo(0),
				() -> assertThat(result.retryAfterMs()).isEqualTo(2000),
				() -> assertThat(result.refreshedToken()).isEqualTo(REFRESHED_TOKEN),
				() -> assertThat(result.entryToken()).isNull()
			);
		}


		@Test
		@DisplayName("[getPosition()] 유효한 진입 증명이지만 다른 userId -> QUEUE_NOT_ENTERED 예외. receipt의 userId와 요청 userId 불일치")
		void getPosition_receiptForDifferentUser_throwsException() {
			// Arrange
			Long userId = 1L;
			Long differentUserId = 999L;
			given(waitingQueueRedisPort.getPosition(userId)).willReturn(null);
			given(waitingQueueRedisPort.getEntryToken(userId)).willReturn(null);
			given(waitingQueueRedisPort.isRejected(userId)).willReturn(false);
			given(queueAuthPort.resolveAndRefresh(QUEUE_TOKEN))
				.willReturn(new QueueAuthPort.ResolvedQueueToken(userId, REFRESHED_TOKEN));
			given(queueAuthPort.resolveEnterReceipt(ENTER_RECEIPT)).willReturn(differentUserId);

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> service.getPosition(QUEUE_TOKEN, ENTER_RECEIPT));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.QUEUE_NOT_ENTERED),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.QUEUE_NOT_ENTERED.getMessage())
			);
		}


		@Test
		@DisplayName("[getPosition()] 진입 증명 없음 + Redis 미반영 -> QUEUE_NOT_ENTERED 예외. 미진입 사용자")
		void getPosition_notEntered_throwsException() {
			// Arrange
			Long userId = 1L;
			given(waitingQueueRedisPort.getPosition(userId)).willReturn(null);
			given(waitingQueueRedisPort.getEntryToken(userId)).willReturn(null);
			given(waitingQueueRedisPort.isRejected(userId)).willReturn(false);
			given(queueAuthPort.resolveAndRefresh(QUEUE_TOKEN))
				.willReturn(new QueueAuthPort.ResolvedQueueToken(userId, REFRESHED_TOKEN));

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> service.getPosition(QUEUE_TOKEN, null));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.QUEUE_NOT_ENTERED),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.QUEUE_NOT_ENTERED.getMessage())
			);
		}


		@Test
		@DisplayName("[getPosition()] 위조된 진입 증명 -> QUEUE_NOT_ENTERED 예외. 유효하지 않은 receipt")
		void getPosition_invalidReceipt_throwsException() {
			// Arrange
			Long userId = 1L;
			String invalidReceipt = "enter:1:1711900000000:wrong-hmac";
			given(waitingQueueRedisPort.getPosition(userId)).willReturn(null);
			given(waitingQueueRedisPort.getEntryToken(userId)).willReturn(null);
			given(waitingQueueRedisPort.isRejected(userId)).willReturn(false);
			given(queueAuthPort.resolveAndRefresh(QUEUE_TOKEN))
				.willReturn(new QueueAuthPort.ResolvedQueueToken(userId, REFRESHED_TOKEN));
			given(queueAuthPort.resolveEnterReceipt(invalidReceipt)).willReturn(null);

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> service.getPosition(QUEUE_TOKEN, invalidReceipt));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.QUEUE_NOT_ENTERED),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.QUEUE_NOT_ENTERED.getMessage())
			);
		}


		@Test
		@DisplayName("[getPosition()] cap 초과로 거부된 사용자 -> QUEUE_CAPACITY_EXCEEDED 예외")
		void getPosition_rejected_throwsCapacityExceeded() {
			// Arrange
			Long userId = 1L;
			given(waitingQueueRedisPort.getPosition(userId)).willReturn(null);
			given(waitingQueueRedisPort.getEntryToken(userId)).willReturn(null);
			given(waitingQueueRedisPort.isRejected(userId)).willReturn(true);
			given(queueAuthPort.resolveAndRefresh(QUEUE_TOKEN))
				.willReturn(new QueueAuthPort.ResolvedQueueToken(userId, REFRESHED_TOKEN));

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> service.getPosition(QUEUE_TOKEN, ENTER_RECEIPT));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.QUEUE_CAPACITY_EXCEEDED),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.QUEUE_CAPACITY_EXCEEDED.getMessage())
			);
		}


		@Test
		@DisplayName("[getPosition()] 구간별 Polling 주기 검증. position 100/1000/10000/100000/100001에 따라 interval 변경")
		void getInterval_variousPositions_returnsCorrectInterval() {
			// Assert
			assertAll(
				() -> assertThat(service.getInterval(1)).isEqualTo(1000),
				() -> assertThat(service.getInterval(100)).isEqualTo(1000),
				() -> assertThat(service.getInterval(101)).isEqualTo(2000),
				() -> assertThat(service.getInterval(1000)).isEqualTo(2000),
				() -> assertThat(service.getInterval(1001)).isEqualTo(5000),
				() -> assertThat(service.getInterval(10000)).isEqualTo(5000),
				() -> assertThat(service.getInterval(10001)).isEqualTo(10000),
				() -> assertThat(service.getInterval(100000)).isEqualTo(10000),
				() -> assertThat(service.getInterval(100001)).isEqualTo(30000)
			);
		}


		@Test
		@DisplayName("[getPosition()] 구간별 Jitter 검증. position에 따라 jitter 범위 변경")
		void getJitter_variousPositions_returnsCorrectJitter() {
			// Assert
			assertAll(
				() -> assertThat(service.getJitter(1)).isEqualTo(0),
				() -> assertThat(service.getJitter(100)).isEqualTo(0),
				() -> assertThat(service.getJitter(101)).isEqualTo(300),
				() -> assertThat(service.getJitter(1000)).isEqualTo(300),
				() -> assertThat(service.getJitter(1001)).isEqualTo(500),
				() -> assertThat(service.getJitter(10000)).isEqualTo(500),
				() -> assertThat(service.getJitter(10001)).isEqualTo(1000),
				() -> assertThat(service.getJitter(100000)).isEqualTo(1000),
				() -> assertThat(service.getJitter(100001)).isEqualTo(4000)
			);
		}
	}
}
