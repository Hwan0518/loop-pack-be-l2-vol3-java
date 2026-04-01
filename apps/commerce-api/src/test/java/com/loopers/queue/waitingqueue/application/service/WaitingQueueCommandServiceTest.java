package com.loopers.queue.waitingqueue.application.service;


import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.loopers.queue.waitingqueue.application.dto.out.QueueEnterOutDto;
import com.loopers.queue.waitingqueue.application.port.out.QueueAuthPort;
import com.loopers.queue.waitingqueue.application.port.out.WaitingQueueProducerPort;
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
	@DisplayName("[enter()] 유효한 userId와 queueToken -> ACCEPTED 상태 반환. Kafka produce 호출 + 토큰 갱신 + 초기 Jitter(0~2000ms)")
	void enter_validInput_returnsAccepted() {
		// Arrange
		Long userId = 1L;
		String queueToken = "valid-queue-token";
		String refreshedToken = "refreshed-token";
		given(queueAuthPort.refreshToken(queueToken)).willReturn(refreshedToken);

		// Act
		QueueEnterOutDto result = service.enter(userId, queueToken);

		// Assert
		assertThat(result.status()).isEqualTo("ACCEPTED");
		assertThat(result.retryAfterMs()).isBetween(0, 1999);
		assertThat(result.refreshedToken()).isEqualTo(refreshedToken);
		verify(waitingQueueProducerPort).sendEnterMessage(userId);
		verify(queueAuthPort).refreshToken(queueToken);
	}
}
