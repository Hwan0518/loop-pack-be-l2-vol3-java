package com.loopers.payment.payment.support.config;


import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;


/**
 * 스케줄러 인프라 설정
 * - @EnableScheduling: 폴링 스케줄러(PaymentPollingScheduler), 주문 만료 스케줄러(OrderExpirationScheduler)에서 사용
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
