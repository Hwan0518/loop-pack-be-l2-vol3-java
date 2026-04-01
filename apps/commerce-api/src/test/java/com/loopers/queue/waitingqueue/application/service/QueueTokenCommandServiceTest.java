package com.loopers.queue.waitingqueue.application.service;


import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.loopers.queue.waitingqueue.application.dto.out.QueueTokenOutDto;
import com.loopers.queue.waitingqueue.application.port.out.QueueAuthPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


@ExtendWith(MockitoExtension.class)
class QueueTokenCommandServiceTest {

	private QueueTokenCommandService queueTokenCommandService;

	@Mock
	private QueueAuthPort queueAuthPort;


	@BeforeEach
	void setUp() {
		queueTokenCommandService = new QueueTokenCommandService(queueAuthPort);
	}


	@Test
	@DisplayName("[issueToken()] 유효한 userId -> QueueTokenOutDto 반환. QueueAuthPort.generateToken을 호출하여 서명 토큰 생성")
	void issueToken_validUserId_returnsQueueTokenOutDto() {
		// Arrange
		Long userId = 1L;
		String expectedToken = "1:9999999999:hmachex";
		given(queueAuthPort.generateToken(userId)).willReturn(expectedToken);

		// Act
		QueueTokenOutDto result = queueTokenCommandService.issueToken(userId);

		// Assert
		assertThat(result.queueToken()).isEqualTo(expectedToken);
		verify(queueAuthPort).generateToken(userId);
	}
}
