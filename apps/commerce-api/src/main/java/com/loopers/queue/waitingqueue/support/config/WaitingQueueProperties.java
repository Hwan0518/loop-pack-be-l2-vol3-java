package com.loopers.queue.waitingqueue.support.config;


import org.springframework.boot.context.properties.ConfigurationProperties;


/**
 * 대기열 설정
 * - topic: Kafka 토픽명
 * - maxCapacity: 대기열 상한
 */
@ConfigurationProperties(prefix = "queue.waiting")
public record WaitingQueueProperties(
	String topic,
	long maxCapacity
) {
}
