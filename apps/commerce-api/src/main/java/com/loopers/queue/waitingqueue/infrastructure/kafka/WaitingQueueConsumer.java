package com.loopers.queue.waitingqueue.infrastructure.kafka;


import com.loopers.queue.waitingqueue.application.service.WaitingQueueConsumerService;
import com.loopers.queue.waitingqueue.support.config.WaitingQueueKafkaConfig;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;


/**
 * 대기열 Kafka Consumer
 * - waiting-queue-topic에서 메시지 읽기 (순서 보장)
 * - Redis INCR → ZADD (엄밀한 FIFO)
 */
@Component
public class WaitingQueueConsumer {

	private static final Logger log = LoggerFactory.getLogger(WaitingQueueConsumer.class);

	// service
	private final WaitingQueueConsumerService waitingQueueConsumerService;


	public WaitingQueueConsumer(WaitingQueueConsumerService waitingQueueConsumerService) {
		this.waitingQueueConsumerService = waitingQueueConsumerService;
	}


	// 1. 대기열 진입 메시지 처리 (순서대로)
	@KafkaListener(
		topics = "${queue.waiting.topic}",
		groupId = "waiting-queue-consumer",
		containerFactory = WaitingQueueKafkaConfig.QUEUE_LISTENER
	)
	public void consume(List<ConsumerRecord<String, byte[]>> records, Acknowledgment ack) {
		for (ConsumerRecord<String, byte[]> record : records) {
			Long userId = Long.parseLong(new String(record.value()));
			waitingQueueConsumerService.processEntry(userId);
		}
		// 모든 레코드 처리 성공 시에만 커밋 (실패 시 예외 전파 → 재시도)
		ack.acknowledge();
	}
}
