package com.loopers.queue.waitingqueue.application.service;


import com.loopers.queue.waitingqueue.application.port.out.WaitingQueueRedisPort;
import com.loopers.queue.waitingqueue.support.config.WaitingQueueProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;


@Service
public class WaitingQueueConsumerService {

	private static final Logger log = LoggerFactory.getLogger(WaitingQueueConsumerService.class);

	// port
	private final WaitingQueueRedisPort waitingQueueRedisPort;

	// config
	private final WaitingQueueProperties properties;

	/**
	 * 대기열 Consumer 서비스
	 * 1. 대기열 적재
	 */

	public WaitingQueueConsumerService(
		WaitingQueueRedisPort waitingQueueRedisPort,
		WaitingQueueProperties properties
	) {
		this.waitingQueueRedisPort = waitingQueueRedisPort;
		this.properties = properties;
	}


	// 1. 대기열 적재 (INCR → ZADD + cap 검사)
	public void processEntry(Long userId) {
		// Redis INCR → ZADD
		waitingQueueRedisPort.addToQueue(userId);

		// cap 검사: ZADD 후 ZCARD 확인, 초과 시 ZREM + 거부 상태 저장
		long queueSize = waitingQueueRedisPort.getQueueSize();
		if (queueSize > properties.maxCapacity()) {
			waitingQueueRedisPort.removeFromQueue(userId);
			waitingQueueRedisPort.markRejected(userId);
			log.warn("대기열 cap 초과로 사용자 제거: userId={}, queueSize={}", userId, queueSize);
		}
	}
}
