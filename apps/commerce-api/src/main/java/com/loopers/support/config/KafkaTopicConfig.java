package com.loopers.support.config;


import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;


/**
 * Kafka 토픽 자동 생성 설정 (commerce-api)
 * - KafkaAdmin이 앱 시작 시 자동 생성. 이미 존재하면 무시.
 */

@Configuration
public class KafkaTopicConfig {

	@Value("${kafka.topic.replication-factor:1}")
	private int replicationFactor;


	// 1. catalog-events (상품/재고/좋아요 이벤트, key=productId)
	@Bean
	public NewTopic catalogEvents() {
		return TopicBuilder.name("catalog-events").partitions(3).replicas(replicationFactor).build();
	}

	// 2. order-events (주문/결제 이벤트, key=orderId)
	@Bean
	public NewTopic orderEvents() {
		return TopicBuilder.name("order-events").partitions(3).replicas(replicationFactor).build();
	}

	// 3. coupon-issue-requests (쿠폰 발급 요청, key=couponTemplateId) — 단일 파티션 순차 처리
	@Bean
	public NewTopic couponIssueRequests() {
		return TopicBuilder.name("coupon-issue-requests").partitions(1).replicas(replicationFactor).build();
	}

	// 4. product-metrics-snapshots (메트릭 스냅샷, key=productId)
	@Bean
	public NewTopic productMetricsSnapshots() {
		return TopicBuilder.name("product-metrics-snapshots").partitions(3).replicas(replicationFactor).build();
	}

}
