package com.loopers.queue.waitingqueue.application.facade;


import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.loopers.queue.waitingqueue.application.dto.out.QueueTokenOutDto;
import com.loopers.queue.waitingqueue.application.service.QueueTokenCommandService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


@ExtendWith(MockitoExtension.class)
class QueueTokenCommandFacadeTest {

	private QueueTokenCommandFacade queueTokenCommandFacade;

	@Mock
	private QueueTokenCommandService queueTokenCommandService;


	@BeforeEach
	void setUp() {
		queueTokenCommandFacade = new QueueTokenCommandFacade(queueTokenCommandService);
	}


	@Test
	@DisplayName("[issueToken()] 유효한 userId -> QueueTokenOutDto 반환. Service에 위임하여 서명 토큰 발급")
	void issueToken_validUserId_delegatesToService() {
		// Arrange
		Long userId = 1L;
		QueueTokenOutDto expectedOutDto = QueueTokenOutDto.of("token-value");
		given(queueTokenCommandService.issueToken(userId)).willReturn(expectedOutDto);

		// Act
		QueueTokenOutDto result = queueTokenCommandFacade.issueToken(userId);

		// Assert
		assertThat(result).isEqualTo(expectedOutDto);
		verify(queueTokenCommandService).issueToken(userId);
	}
}
