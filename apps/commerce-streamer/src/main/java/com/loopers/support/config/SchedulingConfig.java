package com.loopers.support.config;


import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;


/**
 * 스케줄링 설정 (commerce-streamer)
 * - OutboxRelayScheduler (snapshot outbox → Kafka 발행) 활성화
 */

@Configuration
@EnableScheduling
public class SchedulingConfig {
}
