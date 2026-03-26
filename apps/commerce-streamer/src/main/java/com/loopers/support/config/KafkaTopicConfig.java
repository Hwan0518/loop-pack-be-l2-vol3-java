package com.loopers.support.config;


import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;


/**
 * Kafka 토픽 자동 생성 설정 (commerce-streamer)
 */

@Configuration
public class KafkaTopicConfig {

	@Value("${kafka.topic.replication-factor:1}")
	private int replicationFactor;

	@Bean
	public NewTopic catalogEvents() {
		return TopicBuilder.name("catalog-events").partitions(3).replicas(replicationFactor).build();
	}

	@Bean
	public NewTopic orderEvents() {
		return TopicBuilder.name("order-events").partitions(3).replicas(replicationFactor).build();
	}

	@Bean
	public NewTopic couponIssueRequests() {
		return TopicBuilder.name("coupon-issue-requests").partitions(1).replicas(replicationFactor).build();
	}

	@Bean
	public NewTopic productMetricsSnapshots() {
		return TopicBuilder.name("product-metrics-snapshots").partitions(3).replicas(replicationFactor).build();
	}

}
