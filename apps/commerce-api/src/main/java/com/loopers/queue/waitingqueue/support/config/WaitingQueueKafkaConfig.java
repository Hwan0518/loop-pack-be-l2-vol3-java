package com.loopers.queue.waitingqueue.support.config;


import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;


/**
 * 대기열 전용 Kafka Consumer 설정
 * - concurrency=1 (단일 파티션 순차 처리, FIFO 보장)
 * - batch 활성 (poll 결과를 List<ConsumerRecord>로 일괄 전달)
 * - fetch.min.bytes=1 (즉시 소비, 대기열 지연 최소화)
 */
@Configuration
public class WaitingQueueKafkaConfig {

	public static final String QUEUE_LISTENER = "QUEUE_LISTENER";

	private final WaitingQueueProperties waitingQueueProperties;

	public WaitingQueueKafkaConfig(WaitingQueueProperties waitingQueueProperties) {
		this.waitingQueueProperties = waitingQueueProperties;
	}


	// 토픽 자동 생성 (파티션 1개, FIFO 보장)
	@Bean
	public NewTopic waitingQueueTopic() {
		return TopicBuilder.name(waitingQueueProperties.topic())
			.partitions(1)
			.replicas(1)
			.build();
	}


	@Bean(name = QUEUE_LISTENER)
	public ConcurrentKafkaListenerContainerFactory<Object, Object> queueListenerContainerFactory(
		KafkaProperties kafkaProperties
	) {
		Map<String, Object> consumerConfig = new HashMap<>(kafkaProperties.buildConsumerProperties());
		consumerConfig.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1); // 즉시 소비
		consumerConfig.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 100); // 100ms 대기
		consumerConfig.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 500);
		consumerConfig.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"); // 새 그룹에서 메시지 유실 방지
		consumerConfig.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 10000); // 10s (빠른 rebalance)
		consumerConfig.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 3000); // 3s

		ConcurrentKafkaListenerContainerFactory<Object, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
		factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(consumerConfig));
		factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
		factory.setConcurrency(1);
		factory.setBatchListener(true);

		return factory;
	}
}
