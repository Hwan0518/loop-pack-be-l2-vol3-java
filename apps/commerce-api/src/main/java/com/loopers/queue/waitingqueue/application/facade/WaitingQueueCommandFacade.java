package com.loopers.queue.waitingqueue.application.facade;


import com.loopers.queue.waitingqueue.application.dto.out.QueueEnterOutDto;
import com.loopers.queue.waitingqueue.application.service.WaitingQueueCommandService;
import org.springframework.stereotype.Service;


@Service
public class WaitingQueueCommandFacade {

	// service
	private final WaitingQueueCommandService waitingQueueCommandService;

	/**
	 * 대기열 진입 퍼사드
	 * 1. 대기열 진입
	 */

	public WaitingQueueCommandFacade(WaitingQueueCommandService waitingQueueCommandService) {
		this.waitingQueueCommandService = waitingQueueCommandService;
	}


	// 1. 대기열 진입
	public QueueEnterOutDto enter(String queueToken) {
		return waitingQueueCommandService.enter(queueToken);
	}
}
