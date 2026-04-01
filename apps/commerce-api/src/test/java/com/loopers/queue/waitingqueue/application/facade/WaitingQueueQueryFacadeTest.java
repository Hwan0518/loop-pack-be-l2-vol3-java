package com.loopers.queue.waitingqueue.application.facade;


import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.loopers.queue.waitingqueue.application.dto.out.QueuePositionOutDto;
import com.loopers.queue.waitingqueue.application.service.WaitingQueueQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


@ExtendWith(MockitoExtension.class)
@DisplayName("WaitingQueueQueryFacade 테스트")
class WaitingQueueQueryFacadeTest {

	private WaitingQueueQueryFacade facade;

	@Mock
	private WaitingQueueQueryService waitingQueueQueryService;


	@BeforeEach
	void setUp() {
		facade = new WaitingQueueQueryFacade(waitingQueueQueryService);
	}


	@Test
	@DisplayName("[getPosition()] 유효한 요청 -> Service에 위임하여 QueuePositionOutDto 반환")
	void getPosition_validRequest_delegatesToService() {
		// Arrange
		String queueToken = "valid-token";
		String enterReceipt = "enter:1:123:hmac";
		QueuePositionOutDto expectedDto = QueuePositionOutDto.waiting(500, 1000, 2.86, 5000, "refreshed");
		given(waitingQueueQueryService.getPosition(queueToken, enterReceipt)).willReturn(expectedDto);

		// Act
		QueuePositionOutDto result = facade.getPosition(queueToken, enterReceipt);

		// Assert
		assertThat(result).isEqualTo(expectedDto);
		verify(waitingQueueQueryService).getPosition(queueToken, enterReceipt);
	}
}
