package com.loopers.queue.waitingqueue.application.service;


import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.loopers.queue.waitingqueue.application.port.out.WaitingQueueRedisPort;
import com.loopers.queue.waitingqueue.support.config.WaitingQueueProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


@ExtendWith(MockitoExtension.class)
class WaitingQueueConsumerServiceTest {

	private WaitingQueueConsumerService service;

	@Mock
	private WaitingQueueRedisPort waitingQueueRedisPort;

	private static final long MAX_CAPACITY = 1_000_000L;


	@BeforeEach
	void setUp() {
		WaitingQueueProperties properties = new WaitingQueueProperties("waiting-queue-topic", MAX_CAPACITY);
		service = new WaitingQueueConsumerService(waitingQueueRedisPort, properties);
	}


	@Test
	@DisplayName("[processEntry()] 정상 진입 -> Redis INCR + ZADD 호출. cap 미초과 시 ZREM 미호출")
	void processEntry_normalEntry_addsToQueue() {
		// Arrange
		Long userId = 1L;
		given(waitingQueueRedisPort.addToQueue(userId)).willReturn(1L);
		given(waitingQueueRedisPort.getQueueSize()).willReturn(1L);

		// Act
		service.processEntry(userId);

		// Assert
		verify(waitingQueueRedisPort).addToQueue(userId);
		verify(waitingQueueRedisPort).getQueueSize();
		verify(waitingQueueRedisPort, never()).removeFromQueue(userId);
	}


	@Test
	@DisplayName("[processEntry()] cap 초과 -> Redis ZADD 후 ZREM + markRejected 호출. 마지막 진입자 제거 + 거부 상태 저장")
	void processEntry_capacityExceeded_removesAndRejectsUser() {
		// Arrange
		Long userId = 1L;
		given(waitingQueueRedisPort.addToQueue(userId)).willReturn(1_000_001L);
		given(waitingQueueRedisPort.getQueueSize()).willReturn(MAX_CAPACITY + 1);

		// Act
		service.processEntry(userId);

		// Assert
		verify(waitingQueueRedisPort).addToQueue(userId);
		verify(waitingQueueRedisPort).removeFromQueue(userId);
		verify(waitingQueueRedisPort).markRejected(userId);
	}


	@Test
	@DisplayName("[processEntry()] cap 경계값(queueSize == MAX_CAPACITY) -> 제거/거부 미호출. > 조건이므로 정확히 cap일 때는 통과")
	void processEntry_exactlyAtCapacity_noRemovalOrRejection() {
		// Arrange
		Long userId = 1L;
		given(waitingQueueRedisPort.addToQueue(userId)).willReturn(MAX_CAPACITY);
		given(waitingQueueRedisPort.getQueueSize()).willReturn(MAX_CAPACITY);

		// Act
		service.processEntry(userId);

		// Assert
		verify(waitingQueueRedisPort).addToQueue(userId);
		verify(waitingQueueRedisPort).getQueueSize();
		verify(waitingQueueRedisPort, never()).removeFromQueue(userId);
		verify(waitingQueueRedisPort, never()).markRejected(userId);
	}
}
