package com.loopers.queue.waitingqueue.application.facade;


import com.loopers.queue.waitingqueue.application.dto.out.QueueEnterOutDto;
import com.loopers.queue.waitingqueue.application.service.WaitingQueueCommandService;
import org.springframework.stereotype.Service;


/**
 * 대기열 진입 퍼사드
 */
@Service
public class WaitingQueueCommandFacade {

	// service
	private final WaitingQueueCommandService waitingQueueCommandService;


	public WaitingQueueCommandFacade(WaitingQueueCommandService waitingQueueCommandService) {
		this.waitingQueueCommandService = waitingQueueCommandService;
	}


	// 1. 대기열 진입
	public QueueEnterOutDto enter(Long userId, String queueToken) {
		return waitingQueueCommandService.enter(userId, queueToken);
	}
}
