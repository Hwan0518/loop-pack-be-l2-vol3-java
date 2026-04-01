package com.loopers.queue.waitingqueue.infrastructure.kafka;


import com.loopers.queue.waitingqueue.application.port.out.WaitingQueueProducerPort;
import com.loopers.queue.waitingqueue.support.config.WaitingQueueProperties;
import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;


/**
 * 대기열 Kafka Producer
 * - userId를 key로 메시지 발행
 * - acks=all, produce 실패 시 유저에게 에러 반환
 */
@Component
public class WaitingQueueProducer implements WaitingQueueProducerPort {

	private static final Logger log = LoggerFactory.getLogger(WaitingQueueProducer.class);

	private final KafkaTemplate<Object, Object> kafkaTemplate;
	private final WaitingQueueProperties properties;


	public WaitingQueueProducer(
		KafkaTemplate<Object, Object> kafkaTemplate,
		WaitingQueueProperties properties
	) {
		this.kafkaTemplate = kafkaTemplate;
		this.properties = properties;
	}


	// 1. 대기열 진입 메시지 발행
	@Override
	public void sendEnterMessage(Long userId) {
		try {
			kafkaTemplate.send(properties.topic(), userId.toString(), userId.toString())
				.get();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			log.error("대기열 Kafka produce 인터럽트: userId={}", userId, e);
			throw new CoreException(ErrorType.QUEUE_PRODUCE_FAILED);
		} catch (Exception e) {
			log.error("대기열 Kafka produce 실패: userId={}", userId, e);
			throw new CoreException(ErrorType.QUEUE_PRODUCE_FAILED);
		}
	}
}
