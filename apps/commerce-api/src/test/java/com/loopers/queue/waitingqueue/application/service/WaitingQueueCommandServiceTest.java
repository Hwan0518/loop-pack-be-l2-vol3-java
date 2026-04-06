package com.loopers.queue.waitingqueue.application.service;


import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.loopers.queue.waitingqueue.application.dto.out.QueueEnterOutDto;
import com.loopers.queue.waitingqueue.application.dto.out.QueueStatus;
import com.loopers.queue.waitingqueue.application.port.out.QueueAuthPort;
import com.loopers.queue.waitingqueue.application.port.out.WaitingQueueProducerPort;
import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


@ExtendWith(MockitoExtension.class)
class WaitingQueueCommandServiceTest {

	private WaitingQueueCommandService service;

	@Mock
	private WaitingQueueProducerPort waitingQueueProducerPort;

	@Mock
	private QueueAuthPort queueAuthPort;


	@BeforeEach
	void setUp() {
		service = new WaitingQueueCommandService(waitingQueueProducerPort, queueAuthPort);
	}


	@Test
	@DisplayName("[enter()] 유효한 queueToken -> ACCEPTED 상태 반환. 토큰 검증 + Kafka produce 호출 + 초기 Jitter(0~2000ms)")
	void enter_validInput_returnsAccepted() {
		// Arrange
		Long userId = 1L;
		String queueToken = "valid-queue-token";
		String refreshedToken = "refreshed-token";
		given(queueAuthPort.resolveAndRefresh(queueToken))
			.willReturn(new QueueAuthPort.ResolvedQueueToken(userId, refreshedToken));
		given(queueAuthPort.generateEnterReceipt(userId)).willReturn("enter-receipt");

		// Act
		QueueEnterOutDto result = service.enter(queueToken);

		// Assert
		assertThat(result.status()).isEqualTo(QueueStatus.ACCEPTED);
		assertThat(result.retryAfterMs()).isBetween(0, 1999);
		assertThat(result.refreshedToken()).isEqualTo(refreshedToken);
		assertThat(result.enterReceipt()).isEqualTo("enter-receipt");
		verify(queueAuthPort).resolveAndRefresh(queueToken);
		verify(waitingQueueProducerPort).sendEnterMessage(userId);
	}


	@Test
	@DisplayName("[enter()] resolveAndRefresh 예외 -> 예외 전파 + sendEnterMessage 미호출. 인증 실패 시 부수효과 차단")
	void enter_resolveAndRefreshThrows_propagatesExceptionAndNoKafkaProduce() {
		// Arrange
		String queueToken = "invalid-queue-token";
		given(queueAuthPort.resolveAndRefresh(queueToken))
			.willThrow(new CoreException(ErrorType.INVALID_QUEUE_TOKEN));

		// Act
		CoreException exception = assertThrows(CoreException.class,
			() -> service.enter(queueToken));

		// Assert
		assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_QUEUE_TOKEN);
		verify(waitingQueueProducerPort, never()).sendEnterMessage(org.mockito.ArgumentMatchers.anyLong());
	}


	@Test
	@DisplayName("[enter()] sendEnterMessage 예외 -> 예외 전파. Kafka produce 실패 시 에러 반환")
	void enter_sendEnterMessageThrows_propagatesException() {
		// Arrange
		Long userId = 1L;
		String queueToken = "valid-queue-token";
		given(queueAuthPort.resolveAndRefresh(queueToken))
			.willReturn(new QueueAuthPort.ResolvedQueueToken(userId, "refreshed"));
		willThrow(new CoreException(ErrorType.QUEUE_PRODUCE_FAILED))
			.given(waitingQueueProducerPort).sendEnterMessage(userId);

		// Act
		CoreException exception = assertThrows(CoreException.class,
			() -> service.enter(queueToken));

		// Assert
		assertThat(exception.getErrorType()).isEqualTo(ErrorType.QUEUE_PRODUCE_FAILED);
		verify(queueAuthPort).resolveAndRefresh(queueToken);
	}
}
