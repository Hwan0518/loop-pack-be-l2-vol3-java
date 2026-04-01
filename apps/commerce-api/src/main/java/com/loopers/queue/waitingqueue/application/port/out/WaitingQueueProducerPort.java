package com.loopers.queue.waitingqueue.application.port.out;


/**
 * 대기열 Kafka 메시지 발행 포트
 * - acks=1로 produce, 순서 보장은 Consumer에서
 */
public interface WaitingQueueProducerPort {

	// 1. 대기열 진입 메시지 발행
	void sendEnterMessage(Long userId);
}
