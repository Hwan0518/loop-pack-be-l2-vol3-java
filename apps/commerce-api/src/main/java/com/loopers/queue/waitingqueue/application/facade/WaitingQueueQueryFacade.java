package com.loopers.queue.waitingqueue.application.facade;


import com.loopers.queue.waitingqueue.application.dto.out.QueuePositionOutDto;
import com.loopers.queue.waitingqueue.application.service.WaitingQueueQueryService;
import org.springframework.stereotype.Service;


@Service
public class WaitingQueueQueryFacade {

	// service
	private final WaitingQueueQueryService waitingQueueQueryService;

	/**
	 * 대기열 순번 조회 퍼사드
	 * 1. 순번 조회
	 */

	public WaitingQueueQueryFacade(WaitingQueueQueryService waitingQueueQueryService) {
		this.waitingQueueQueryService = waitingQueueQueryService;
	}


	// 1. 순번 조회
	public QueuePositionOutDto getPosition(String queueToken, String enterReceipt) {
		return waitingQueueQueryService.getPosition(queueToken, enterReceipt);
	}
}
