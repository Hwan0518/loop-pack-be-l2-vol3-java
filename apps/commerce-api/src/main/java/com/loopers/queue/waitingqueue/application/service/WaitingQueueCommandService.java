package com.loopers.queue.waitingqueue.application.service;


import com.loopers.queue.waitingqueue.application.dto.out.QueueEnterOutDto;
import com.loopers.queue.waitingqueue.application.dto.out.QueueStatus;
import com.loopers.queue.waitingqueue.application.port.out.QueueAuthPort;
import com.loopers.queue.waitingqueue.application.port.out.WaitingQueueProducerPort;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;


@Service
public class WaitingQueueCommandService {

	private static final int INITIAL_JITTER_BOUND = 2000;

	// port
	private final WaitingQueueProducerPort waitingQueueProducerPort;
	private final QueueAuthPort queueAuthPort;

	/**
	 * 대기열 진입 서비스
	 * 1. 대기열 진입
	 */

	public WaitingQueueCommandService(
		WaitingQueueProducerPort waitingQueueProducerPort,
		QueueAuthPort queueAuthPort
	) {
		this.waitingQueueProducerPort = waitingQueueProducerPort;
		this.queueAuthPort = queueAuthPort;
	}


	// 1. 대기열 진입 (토큰 검증 + Kafka produce + 초기 Jitter 응답 + 진입 증명)
	public QueueEnterOutDto enter(String queueToken) {
		// 서명 토큰 검증 + userId 추출 + 갱신 (단일 호출)
		QueueAuthPort.ResolvedQueueToken resolved = queueAuthPort.resolveAndRefresh(queueToken);

		// Kafka produce (인증 성공 후에만 실행)
		waitingQueueProducerPort.sendEnterMessage(resolved.userId());

		// 진입 증명 발급 (stateless — "진입했음"을 증명하는 서명)
		String enterReceipt = queueAuthPort.generateEnterReceipt(resolved.userId());

		// 초기 Jitter (0 ~ 2000ms)
		int retryAfterMs = ThreadLocalRandom.current().nextInt(0, INITIAL_JITTER_BOUND);

		return QueueEnterOutDto.of(QueueStatus.ACCEPTED, retryAfterMs, resolved.refreshedToken(), enterReceipt);
	}
}
