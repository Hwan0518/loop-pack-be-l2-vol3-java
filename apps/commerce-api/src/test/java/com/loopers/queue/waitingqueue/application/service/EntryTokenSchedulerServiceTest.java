package com.loopers.queue.waitingqueue.application.service;


import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.loopers.queue.waitingqueue.application.port.out.WaitingQueueRedisPort;
import com.loopers.queue.waitingqueue.support.config.QueueTokenProperties;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


@ExtendWith(MockitoExtension.class)
class EntryTokenSchedulerServiceTest {

	private EntryTokenSchedulerService service;

	@Mock
	private WaitingQueueRedisPort waitingQueueRedisPort;

	private static final long TOKEN_TTL_SECONDS = 300L;


	@BeforeEach
	void setUp() {
		QueueTokenProperties properties = new QueueTokenProperties("test-secret", TOKEN_TTL_SECONDS);
		service = new EntryTokenSchedulerService(waitingQueueRedisPort, properties);
	}


	@Test
	@DisplayName("[issueEntryTokens()] 대기열에 사용자 존재 -> ZPOPMIN 18명 + SET EX 호출. Lua script로 원자적 처리")
	void issueEntryTokens_usersInQueue_issuesTokens() {
		// Arrange
		given(waitingQueueRedisPort.issueEntryTokens(18, TOKEN_TTL_SECONDS))
			.willReturn(List.of("1", "2", "3"));

		// Act
		service.issueEntryTokens();

		// Assert
		verify(waitingQueueRedisPort).issueEntryTokens(18, TOKEN_TTL_SECONDS);
	}


	@Test
	@DisplayName("[issueEntryTokens()] 대기열이 비어있음 -> 빈 리스트 반환. 에러 없이 종료")
	void issueEntryTokens_emptyQueue_returnsEmpty() {
		// Arrange
		given(waitingQueueRedisPort.issueEntryTokens(18, TOKEN_TTL_SECONDS))
			.willReturn(List.of());

		// Act
		service.issueEntryTokens();

		// Assert
		verify(waitingQueueRedisPort).issueEntryTokens(18, TOKEN_TTL_SECONDS);
	}
}
