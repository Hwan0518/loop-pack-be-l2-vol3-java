package com.loopers.queue.waitingqueue.infrastructure.kafka;


import com.loopers.queue.waitingqueue.application.service.WaitingQueueConsumerService;
import com.loopers.queue.waitingqueue.support.config.WaitingQueueKafkaConfig;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.BatchListenerFailedException;
import org.springframework.stereotype.Component;


/**
 * 대기열 Kafka Consumer
 * - property 기반 topic/groupId로 메시지 읽기 (테스트 격리 지원)
 * - Redis INCR → ZADD (엄밀한 FIFO)
 * - BatchListenerFailedException: 배치 부분 실패 시 처리 완료 record의 offset은 커밋,
 *   실패 record부터만 재전달 (이미 ZADD된 사용자의 FIFO 순서 보존)
 */
@Component
@ConditionalOnProperty(name = "queue.waiting.consumer.enabled", havingValue = "true", matchIfMissing = true)
public class WaitingQueueConsumer {

	private static final Logger log = LoggerFactory.getLogger(WaitingQueueConsumer.class);

	// service
	private final WaitingQueueConsumerService waitingQueueConsumerService;


	public WaitingQueueConsumer(WaitingQueueConsumerService waitingQueueConsumerService) {
		this.waitingQueueConsumerService = waitingQueueConsumerService;
	}


	// 1. 대기열 진입 메시지 처리 (순서대로)
	@KafkaListener(
		id = "waitingQueueConsumerListener",
		topics = "${queue.waiting.topic}",
		groupId = "${queue.waiting.consumer.group-id:waiting-queue-consumer}",
		containerFactory = WaitingQueueKafkaConfig.QUEUE_LISTENER
	)
	public void consume(List<ConsumerRecord<String, byte[]>> records) {
		for (int i = 0; i < records.size(); i++) {
			ConsumerRecord<String, byte[]> record = records.get(i);
			try {
				Long userId = Long.parseLong(new String(record.value(), StandardCharsets.UTF_8));
				waitingQueueConsumerService.processEntry(userId);
			} catch (NumberFormatException e) {
				// poison pill 스킵 — 파싱 불가 메시지는 재시도해도 동일 결과
				log.error("유효하지 않은 userId 형식: partition={}, offset={}, value={}",
					record.partition(), record.offset(),
					new String(record.value(), StandardCharsets.UTF_8), e);
			} catch (Exception e) {
				// 처리 완료 record(0..i-1) offset은 DefaultErrorHandler가 커밋, i부터 재전달
				log.error("대기열 진입 처리 실패: partition={}, offset={}",
					record.partition(), record.offset(), e);
				throw new BatchListenerFailedException("대기열 진입 처리 실패", e, record);
			}
		}
	}
}
