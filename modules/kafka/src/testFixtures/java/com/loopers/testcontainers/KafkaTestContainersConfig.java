package com.loopers.testcontainers;


import org.springframework.context.annotation.Configuration;
import org.testcontainers.kafka.KafkaContainer;


/**
 * Kafka TestContainers 설정
 */
@Configuration
public class KafkaTestContainersConfig {

	private static final KafkaContainer kafkaContainer;

	static {
		kafkaContainer = new KafkaContainer("apache/kafka-native:latest");
		kafkaContainer.start();

		System.setProperty("spring.kafka.bootstrap-servers", kafkaContainer.getBootstrapServers());
	}
}
