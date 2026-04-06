package com.loopers.queue.waitingqueue.application.service;


import com.loopers.queue.waitingqueue.application.dto.out.QueueTokenOutDto;
import com.loopers.queue.waitingqueue.application.port.out.QueueAuthPort;
import org.springframework.stereotype.Service;


@Service
public class QueueTokenCommandService {

	// port
	private final QueueAuthPort queueAuthPort;

	/**
	 * 대기열 토큰 발급 서비스
	 * 1. 서명 토큰 발급
	 */

	public QueueTokenCommandService(QueueAuthPort queueAuthPort) {
		this.queueAuthPort = queueAuthPort;
	}


	// 1. 서명 토큰 발급
	public QueueTokenOutDto issueToken(Long userId) {
		String token = queueAuthPort.generateToken(userId);
		return QueueTokenOutDto.of(token);
	}
}
