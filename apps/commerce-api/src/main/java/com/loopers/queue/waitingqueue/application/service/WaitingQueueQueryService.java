package com.loopers.queue.waitingqueue.application.service;


import com.loopers.queue.waitingqueue.application.dto.out.QueuePositionOutDto;
import com.loopers.queue.waitingqueue.application.port.out.QueueAuthPort;
import com.loopers.queue.waitingqueue.application.port.out.WaitingQueueRedisPort;
import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;


/**
 * 대기열 순번 조회 서비스
 * - ZRANK → position 계산
 * - 구간별 Polling 주기 + Jitter 적용
 */
@Service
public class WaitingQueueQueryService {

	private static final double TPS = 175.0;

	// port
	private final WaitingQueueRedisPort waitingQueueRedisPort;
	private final QueueAuthPort queueAuthPort;


	public WaitingQueueQueryService(
		WaitingQueueRedisPort waitingQueueRedisPort,
		QueueAuthPort queueAuthPort
	) {
		this.waitingQueueRedisPort = waitingQueueRedisPort;
		this.queueAuthPort = queueAuthPort;
	}


	// 1. 순번 조회
	public QueuePositionOutDto getPosition(Long userId, String queueToken) {
		Long rank = waitingQueueRedisPort.getPosition(userId);

		// 갱신된 서명 토큰
		String refreshedToken = queueAuthPort.refreshToken(queueToken);

		// 대기열에 존재
		if (rank != null) {
			long position = rank + 1;
			long totalWaiting = waitingQueueRedisPort.getQueueSize();
			int interval = getInterval(position);
			int jitter = getJitter(position);
			int retryAfterMs = interval + ThreadLocalRandom.current().nextInt(-jitter, jitter + 1);
			double estimatedWaitSeconds = position / TPS;

			return QueuePositionOutDto.waiting(position, totalWaiting, estimatedWaitSeconds, retryAfterMs, refreshedToken);
		}

		// 대기열에 없음 → 입장 토큰 확인
		String entryToken = waitingQueueRedisPort.getEntryToken(userId);
		if (entryToken != null) {
			return QueuePositionOutDto.tokenIssued(entryToken, refreshedToken);
		}

		// cap 초과로 거부된 사용자
		if (waitingQueueRedisPort.isRejected(userId)) {
			throw new CoreException(ErrorType.QUEUE_CAPACITY_EXCEEDED);
		}

		// 미진입 또는 Consumer 미처리
		throw new CoreException(ErrorType.QUEUE_NOT_ENTERED);
	}


	// position 기준 Polling 주기 (ms)
	int getInterval(long position) {
		if (position <= 100) return 1000;
		if (position <= 1000) return 2000;
		if (position <= 10000) return 5000;
		if (position <= 100000) return 10000;
		return 30000;
	}


	// position 기준 Jitter 범위 (ms)
	int getJitter(long position) {
		if (position <= 100) return 0;
		if (position <= 1000) return 300;
		if (position <= 10000) return 500;
		if (position <= 100000) return 1000;
		return 4000;
	}
}
