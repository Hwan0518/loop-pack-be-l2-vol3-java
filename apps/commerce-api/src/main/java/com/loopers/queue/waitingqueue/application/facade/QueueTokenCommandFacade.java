package com.loopers.queue.waitingqueue.application.facade;


import com.loopers.queue.waitingqueue.application.dto.out.QueueTokenOutDto;
import com.loopers.queue.waitingqueue.application.service.QueueTokenCommandService;
import org.springframework.stereotype.Service;


@Service
public class QueueTokenCommandFacade {

	// service
	private final QueueTokenCommandService queueTokenCommandService;

	/**
	 * 대기열 토큰 발급 퍼사드
	 * 1. 서명 토큰 발급
	 */

	public QueueTokenCommandFacade(QueueTokenCommandService queueTokenCommandService) {
		this.queueTokenCommandService = queueTokenCommandService;
	}


	// 1. 서명 토큰 발급
	public QueueTokenOutDto issueToken(Long userId) {
		return queueTokenCommandService.issueToken(userId);
	}
}
