package com.loopers.queue.waitingqueue.support.config;


import org.springframework.boot.context.properties.ConfigurationProperties;


/**
 * 대기열 토큰 설정
 * - secretKey: HMAC 서명에 사용할 비밀키
 * - tokenTtlSeconds: 토큰 유효 시간 (초)
 */
@ConfigurationProperties(prefix = "queue.token")
public record QueueTokenProperties(
	String secretKey,
	long tokenTtlSeconds
) {
}
