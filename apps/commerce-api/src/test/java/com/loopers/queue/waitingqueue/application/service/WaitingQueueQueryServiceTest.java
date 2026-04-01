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


	@BeforeEach
	void setUp() {
		service = new WaitingQueueQueryService(waitingQueueRedisPort, queueAuthPort);
	}


	@Nested
	@DisplayName("getPosition()")
	class GetPositionTest {

		@Test
		@DisplayName("[getPosition()] 대기열에 존재 (rank=0) -> position=1, estimatedWaitSeconds 포함. 1-based 순번으로 변환")
		void getPosition_inQueue_returnsPosition() {
			// Arrange
			Long userId = 1L;
			given(waitingQueueRedisPort.getPosition(userId)).willReturn(0L);
			given(queueAuthPort.refreshToken(QUEUE_TOKEN)).willReturn(REFRESHED_TOKEN);

			// Act
			QueuePositionOutDto result = service.getPosition(userId, QUEUE_TOKEN);

			// Assert
			assertAll(
				() -> assertThat(result.position()).isEqualTo(1),
				() -> assertThat(result.estimatedWaitSeconds()).isGreaterThan(0),
				() -> assertThat(result.retryAfterMs()).isGreaterThan(0),
				() -> assertThat(result.refreshedToken()).isEqualTo(REFRESHED_TOKEN),
				() -> assertThat(result.entryToken()).isNull()
			);
		}


		@Test
		@DisplayName("[getPosition()] 대기열에 없고 입장 토큰 존재 -> position=0 + entryToken 반환. 이미 입장 처리된 사용자")
		void getPosition_tokenIssued_returnsToken() {
			// Arrange
			Long userId = 1L;
			given(waitingQueueRedisPort.getPosition(userId)).willReturn(null);
			given(waitingQueueRedisPort.getEntryToken(userId)).willReturn("entry-token-value");
			given(queueAuthPort.refreshToken(QUEUE_TOKEN)).willReturn(REFRESHED_TOKEN);

			// Act
			QueuePositionOutDto result = service.getPosition(userId, QUEUE_TOKEN);

			// Assert
			assertAll(
				() -> assertThat(result.position()).isEqualTo(0),
				() -> assertThat(result.entryToken()).isEqualTo("entry-token-value"),
				() -> assertThat(result.refreshedToken()).isEqualTo(REFRESHED_TOKEN)
			);
		}


		@Test
		@DisplayName("[getPosition()] 대기열에 없고 입장 토큰도 없음 -> QUEUE_NOT_ENTERED 예외. 미진입 또는 Consumer 미처리")
		void getPosition_notEntered_throwsException() {
			// Arrange
			Long userId = 1L;
			given(waitingQueueRedisPort.getPosition(userId)).willReturn(null);
			given(waitingQueueRedisPort.getEntryToken(userId)).willReturn(null);
			given(waitingQueueRedisPort.isRejected(userId)).willReturn(false);
			given(queueAuthPort.refreshToken(QUEUE_TOKEN)).willReturn(REFRESHED_TOKEN);

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> service.getPosition(userId, QUEUE_TOKEN));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.QUEUE_NOT_ENTERED),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.QUEUE_NOT_ENTERED.getMessage())
			);
		}


		@Test
		@DisplayName("[getPosition()] cap 초과로 거부된 사용자 -> QUEUE_CAPACITY_EXCEEDED 예외. 대기열이 가득 차 진입 거부됨")
		void getPosition_rejected_throwsCapacityExceeded() {
			// Arrange
			Long userId = 1L;
			given(waitingQueueRedisPort.getPosition(userId)).willReturn(null);
			given(waitingQueueRedisPort.getEntryToken(userId)).willReturn(null);
			given(waitingQueueRedisPort.isRejected(userId)).willReturn(true);
			given(queueAuthPort.refreshToken(QUEUE_TOKEN)).willReturn(REFRESHED_TOKEN);

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> service.getPosition(userId, QUEUE_TOKEN));

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
