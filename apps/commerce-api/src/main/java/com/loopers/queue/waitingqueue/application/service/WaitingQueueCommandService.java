package com.loopers.queue.waitingqueue.application.service;


import com.loopers.queue.waitingqueue.application.dto.out.QueueEnterOutDto;
import com.loopers.queue.waitingqueue.application.port.out.QueueAuthPort;
import com.loopers.queue.waitingqueue.application.port.out.WaitingQueueProducerPort;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;


/**
 * 대기열 진입 서비스
 * - Kafka produce로 진입 요청 전송
 */
@Service
public class WaitingQueueCommandService {

	private static final int INITIAL_JITTER_BOUND = 2000;

	// port
	private final WaitingQueueProducerPort waitingQueueProducerPort;
	private final QueueAuthPort queueAuthPort;


	public WaitingQueueCommandService(
		WaitingQueueProducerPort waitingQueueProducerPort,
		QueueAuthPort queueAuthPort
	) {
		this.waitingQueueProducerPort = waitingQueueProducerPort;
		this.queueAuthPort = queueAuthPort;
	}


	// 1. 대기열 진입 (Kafka produce + 초기 Jitter 응답)
	public QueueEnterOutDto enter(Long userId, String queueToken) {
		// Kafka produce
		waitingQueueProducerPort.sendEnterMessage(userId);

		// 갱신된 서명 토큰
		String refreshedToken = queueAuthPort.refreshToken(queueToken);

		// 초기 Jitter (0 ~ 2000ms)
		int retryAfterMs = ThreadLocalRandom.current().nextInt(0, INITIAL_JITTER_BOUND);

		return QueueEnterOutDto.of("ACCEPTED", retryAfterMs, refreshedToken);
	}
}
