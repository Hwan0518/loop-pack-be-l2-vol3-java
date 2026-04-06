package com.loopers.queue.waitingqueue.application.service;


import com.loopers.queue.waitingqueue.application.dto.out.QueuePositionOutDto;
import com.loopers.queue.waitingqueue.application.port.out.QueueAuthPort;
import com.loopers.queue.waitingqueue.application.port.out.WaitingQueueRedisPort;
import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;


@Service
public class WaitingQueueQueryService {

	// 초당 입장 처리량 추정 (배치 18명 / 100ms ≈ 180 TPS → 보수적 175)
	private static final double TPS = 175.0;

	// port
	private final WaitingQueueRedisPort waitingQueueRedisPort;
	private final QueueAuthPort queueAuthPort;

	/**
	 * 대기열 순번 조회 서비스
	 * 1. 순번 조회
	 */

	public WaitingQueueQueryService(
		WaitingQueueRedisPort waitingQueueRedisPort,
		QueueAuthPort queueAuthPort
	) {
		this.waitingQueueRedisPort = waitingQueueRedisPort;
		this.queueAuthPort = queueAuthPort;
	}


	// 1. 순번 조회
	public QueuePositionOutDto getPosition(String queueToken, String enterReceipt) {
		// 서명 토큰 검증 + userId 추출 + 갱신 (단일 호출)
		QueueAuthPort.ResolvedQueueToken resolved = queueAuthPort.resolveAndRefresh(queueToken);
		Long userId = resolved.userId();
		String refreshedToken = resolved.refreshedToken();

		Long rank = waitingQueueRedisPort.getPosition(userId);

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

		// 진입 증명 있음 → userId 바인딩 + 만료 검증 후 PROCESSING
		if (enterReceipt != null) {
			Long receiptUserId = queueAuthPort.resolveEnterReceipt(enterReceipt);
			if (receiptUserId != null && receiptUserId.equals(userId)) {
				return QueuePositionOutDto.processing(refreshedToken);
			}
		}

		// 미진입
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
