package com.loopers.queue.waitingqueue.support.config;


import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;


/**
 * 대기열 전용 Kafka Consumer 설정
 * - concurrency=1 (단일 파티션 순차 처리, FIFO 보장)
 * - batch 활성 (poll 결과를 List<ConsumerRecord>로 일괄 전달)
 * - fetch.min.bytes=1 (즉시 소비, 대기열 지연 최소화)
 * - BatchListenerFailedException + DefaultErrorHandler: 배치 부분 실패 시 FIFO 보존
 */
@Configuration
public class WaitingQueueKafkaConfig {

	public static final String QUEUE_LISTENER = "QUEUE_LISTENER";

	private final WaitingQueueProperties waitingQueueProperties;

	public WaitingQueueKafkaConfig(WaitingQueueProperties waitingQueueProperties) {
		this.waitingQueueProperties = waitingQueueProperties;
	}


	// 메인 토픽 (파티션 1개, FIFO 보장)
	@Bean
	public NewTopic waitingQueueTopic() {
		return TopicBuilder.name(waitingQueueProperties.topic())
			.partitions(1)
			.replicas(1)
			.build();
	}


	// DLT 토픽 (auto.create.topics.enable=false이므로 명시 생성)
	@Bean
	public NewTopic waitingQueueDltTopic() {
		return TopicBuilder.name(waitingQueueProperties.topic() + ".DLT")
			.partitions(1)
			.replicas(1)
			.build();
	}


	// queue 전용 ErrorHandler — byte[] value 대응 DLT publish
	@Bean
	public DefaultErrorHandler queueErrorHandler(KafkaProperties kafkaProperties) {
		// consumer가 ByteArrayDeserializer를 사용하므로 DLT publish도 ByteArraySerializer 필요
		Map<String, Object> props = new HashMap<>(kafkaProperties.buildProducerProperties());
		props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
		ProducerFactory<Object, Object> producerFactory = new DefaultKafkaProducerFactory<>(props);
		KafkaTemplate<Object, Object> dltTemplate = new KafkaTemplate<>(producerFactory);

		DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(dltTemplate,
			(record, ex) -> new TopicPartition(record.topic() + ".DLT", 0));
		ExponentialBackOff backOff = new ExponentialBackOff(1000L, 2.0);
		backOff.setMaxElapsedTime(7000L); // 최대 7초 (1 + 2 + 4)
		return new DefaultErrorHandler(recoverer, backOff);
	}


	@Bean(name = QUEUE_LISTENER)
	public ConcurrentKafkaListenerContainerFactory<Object, Object> queueListenerContainerFactory(
		KafkaProperties kafkaProperties,
		@Qualifier("queueErrorHandler") DefaultErrorHandler queueErrorHandler
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
		factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.BATCH);
		factory.setCommonErrorHandler(queueErrorHandler);
		factory.setConcurrency(1);
		factory.setBatchListener(true);

		return factory;
	}
}
