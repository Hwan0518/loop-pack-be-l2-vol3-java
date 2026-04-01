package com.loopers.queue.waitingqueue.infrastructure.kafka;


import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

import com.loopers.queue.waitingqueue.support.config.WaitingQueueProperties;
import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;


/**
 * WaitingQueueProducer 단위 테스트
 * - Kafka 메시지 발행 성공/실패 시나리오 검증
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WaitingQueueProducer 단위 테스트")
class WaitingQueueProducerTest {

	private WaitingQueueProducer producer;

	@Mock
	private KafkaTemplate<Object, Object> kafkaTemplate;

	private static final String TOPIC = "waiting-queue-topic";


	@BeforeEach
	void setUp() {
		WaitingQueueProperties properties = new WaitingQueueProperties(TOPIC, 1000L);
		producer = new WaitingQueueProducer(kafkaTemplate, properties);
	}


	@Test
	@DisplayName("[sendEnterMessage()] 정상 발행 -> 예외 없이 완료. userId를 key/value로 Kafka 메시지 발행")
	void sendEnterMessage_success_noException() {
		// Arrange
		Long userId = 1L;
		CompletableFuture<SendResult<Object, Object>> future = CompletableFuture.completedFuture(null);
		given(kafkaTemplate.send(TOPIC, userId.toString(), userId.toString()))
			.willReturn(future);

		// Act & Assert
		assertDoesNotThrow(() -> producer.sendEnterMessage(userId));
	}


	@Test
	@DisplayName("[sendEnterMessage()] ExecutionException 발생 -> QUEUE_PRODUCE_FAILED 예외. Kafka 발행 실패 시 CoreException 변환")
	void sendEnterMessage_executionException_throwsCoreException() {
		// Arrange
		Long userId = 2L;
		CompletableFuture<SendResult<Object, Object>> future = CompletableFuture.failedFuture(
			new RuntimeException("Kafka broker unavailable"));
		given(kafkaTemplate.send(TOPIC, userId.toString(), userId.toString()))
			.willReturn(future);

		// Act
		CoreException exception = assertThrows(CoreException.class,
			() -> producer.sendEnterMessage(userId));

		// Assert
		assertAll(
			() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.QUEUE_PRODUCE_FAILED),
			() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.QUEUE_PRODUCE_FAILED.getMessage())
		);
	}


	@Test
	@DisplayName("[sendEnterMessage()] InterruptedException 발생 -> QUEUE_PRODUCE_FAILED 예외 + 인터럽트 플래그 복구")
	void sendEnterMessage_interruptedException_throwsCoreException() {
		// Arrange
		Long userId = 3L;
		CompletableFuture<SendResult<Object, Object>> future = new CompletableFuture<>();
		// InterruptedException은 CompletableFuture.get() 호출 시 스레드가 인터럽트되면 발생
		// 미완료 CompletableFuture에 대해 get()을 호출하면 블로킹되므로,
		// 현재 스레드를 미리 인터럽트하여 get()에서 즉시 InterruptedException이 발생하도록 한다.
		given(kafkaTemplate.send(TOPIC, userId.toString(), userId.toString()))
			.willReturn(future);
		try {
			Thread.currentThread().interrupt();

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> producer.sendEnterMessage(userId));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.QUEUE_PRODUCE_FAILED),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.QUEUE_PRODUCE_FAILED.getMessage()),
				() -> assertThat(Thread.currentThread().isInterrupted()).isTrue()
			);
		} finally {
			Thread.interrupted();
		}
	}
}
