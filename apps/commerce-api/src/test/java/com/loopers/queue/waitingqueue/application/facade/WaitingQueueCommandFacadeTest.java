package com.loopers.queue.waitingqueue.application.facade;


import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.loopers.queue.waitingqueue.application.dto.out.QueueEnterOutDto;
import com.loopers.queue.waitingqueue.application.service.WaitingQueueCommandService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


@ExtendWith(MockitoExtension.class)
@DisplayName("WaitingQueueCommandFacade 테스트")
class WaitingQueueCommandFacadeTest {

	private WaitingQueueCommandFacade facade;

	@Mock
	private WaitingQueueCommandService waitingQueueCommandService;


	@BeforeEach
	void setUp() {
		facade = new WaitingQueueCommandFacade(waitingQueueCommandService);
	}


	@Test
	@DisplayName("[enter()] 유효한 요청 -> Service에 위임하여 QueueEnterOutDto 반환")
	void enter_validRequest_delegatesToService() {
		// Arrange
		String queueToken = "valid-token";
		QueueEnterOutDto expectedDto = QueueEnterOutDto.of("ACCEPTED", 1500, "refreshed", "receipt");
		given(waitingQueueCommandService.enter(queueToken)).willReturn(expectedDto);

		// Act
		QueueEnterOutDto result = facade.enter(queueToken);

		// Assert
		assertThat(result).isEqualTo(expectedDto);
		verify(waitingQueueCommandService).enter(queueToken);
	}
}
