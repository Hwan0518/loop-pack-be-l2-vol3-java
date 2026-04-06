package com.loopers.queue.waitingqueue.infrastructure.kafka;


import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;

import com.loopers.queue.waitingqueue.application.service.WaitingQueueConsumerService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.listener.BatchListenerFailedException;


/**
 * WaitingQueueConsumer 단위 테스트
 * - 배치 부분 실패 시 BatchListenerFailedException 전파 검증
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WaitingQueueConsumer 단위 테스트")
class WaitingQueueConsumerTest {

	private WaitingQueueConsumer consumer;

	@Mock
	private WaitingQueueConsumerService waitingQueueConsumerService;

	private static final String TOPIC = "waiting-queue-topic";


	@BeforeEach
	void setUp() {
		consumer = new WaitingQueueConsumer(waitingQueueConsumerService);
	}


	@Test
	@DisplayName("[consume()] 전체 배치 정상 처리 -> 예외 없이 완료")
	void consume_allSuccess_noException() {
		// Arrange
		List<ConsumerRecord<String, byte[]>> records = List.of(
			createRecord(0, 1L),
			createRecord(1, 2L),
			createRecord(2, 3L)
		);
		willDoNothing().given(waitingQueueConsumerService).processEntry(1L);
		willDoNothing().given(waitingQueueConsumerService).processEntry(2L);
		willDoNothing().given(waitingQueueConsumerService).processEntry(3L);

		// Act & Assert
		assertDoesNotThrow(() -> consumer.consume(records));
	}


	@Test
	@DisplayName("[consume()] 배치 중간 record 처리 실패 -> BatchListenerFailedException. cause + failed record 포함")
	void consume_midBatchFailure_throwsBatchListenerFailedException() {
		// Arrange — 2번째 record(index=1, userId=2)에서 예외 발생
		RuntimeException cause = new RuntimeException("Redis connection refused");
		List<ConsumerRecord<String, byte[]>> records = List.of(
			createRecord(0, 1L),
			createRecord(1, 2L),
			createRecord(2, 3L)
		);
		willDoNothing().given(waitingQueueConsumerService).processEntry(1L);
		willThrow(cause).given(waitingQueueConsumerService).processEntry(2L);

		// Act
		BatchListenerFailedException exception = assertThrows(
			BatchListenerFailedException.class,
			() -> consumer.consume(records)
		);

		// Assert — cause exception + failed record 확인
		assertThat(exception.getCause()).isSameAs(cause);
		assertThat(exception.getRecord()).isEqualTo(records.get(1));
	}


	@Test
	@DisplayName("[consume()] poison pill(숫자 파싱 불가) -> 해당 record 스킵, 이후 record 정상 처리")
	void consume_poisonPill_skippedAndContinues() {
		// Arrange — 1번째 record가 파싱 불가, 2번째는 정상
		ConsumerRecord<String, byte[]> poisonRecord = new ConsumerRecord<>(
			TOPIC, 0, 0, "bad", "not-a-number".getBytes(StandardCharsets.UTF_8));
		ConsumerRecord<String, byte[]> validRecord = createRecord(1, 42L);
		List<ConsumerRecord<String, byte[]>> records = List.of(poisonRecord, validRecord);
		willDoNothing().given(waitingQueueConsumerService).processEntry(42L);

		// Act & Assert — 예외 없이 완료 (poison pill은 스킵)
		assertDoesNotThrow(() -> consumer.consume(records));
	}


	// helper — ConsumerRecord 생성
	private ConsumerRecord<String, byte[]> createRecord(long offset, Long userId) {
		return new ConsumerRecord<>(
			TOPIC, 0, offset,
			userId.toString(),
			userId.toString().getBytes(StandardCharsets.UTF_8)
		);
	}
}
