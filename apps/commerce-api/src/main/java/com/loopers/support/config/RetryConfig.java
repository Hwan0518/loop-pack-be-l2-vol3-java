package com.loopers.support.config;


import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;


/**
 * Spring Retry 설정
 * - 애플리케이션 전체에서 @Retryable 활성화
 */
@Configuration
@EnableRetry
public class RetryConfig {
}
