package com.loopers.queue.waitingqueue.interfaces.web.controller;


import com.loopers.queue.waitingqueue.application.dto.out.QueueEnterOutDto;
import com.loopers.queue.waitingqueue.application.dto.out.QueuePositionOutDto;
import com.loopers.queue.waitingqueue.application.facade.WaitingQueueCommandFacade;
import com.loopers.queue.waitingqueue.application.facade.WaitingQueueQueryFacade;
import com.loopers.queue.waitingqueue.application.port.out.QueueAuthPort;
import com.loopers.queue.waitingqueue.interfaces.web.response.QueueEnterResponse;
import com.loopers.queue.waitingqueue.interfaces.web.response.QueuePositionResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


/**
 * 대기열 컨트롤러
 * - POST /queue/enter: 대기열 진입 (서명 토큰 인증)
 * - GET /queue/position: 순번 조회 (서명 토큰 인증)
 */
@RestController
@RequestMapping("/queue")
public class WaitingQueueController {

	// facade
	private final WaitingQueueCommandFacade waitingQueueCommandFacade;
	private final WaitingQueueQueryFacade waitingQueueQueryFacade;

	// auth
	private final QueueAuthPort queueAuthPort;


	public WaitingQueueController(
		WaitingQueueCommandFacade waitingQueueCommandFacade,
		WaitingQueueQueryFacade waitingQueueQueryFacade,
		QueueAuthPort queueAuthPort
	) {
		this.waitingQueueCommandFacade = waitingQueueCommandFacade;
		this.waitingQueueQueryFacade = waitingQueueQueryFacade;
		this.queueAuthPort = queueAuthPort;
	}


	// 1. 대기열 진입 (서명 토큰으로 경량 인증)
	@PostMapping("/enter")
	public ResponseEntity<QueueEnterResponse> enter(
		@RequestHeader(value = "X-Queue-Token") String queueToken
	) {
		// 서명 토큰 인증 (DB 안 침)
		Long userId = queueAuthPort.resolveUserId(queueToken);

		// 대기열 진입
		QueueEnterOutDto outDto = waitingQueueCommandFacade.enter(userId, queueToken);

		return ResponseEntity.ok(QueueEnterResponse.from(outDto));
	}


	// 2. 순번 조회 (서명 토큰으로 경량 인증)
	@GetMapping("/position")
	public ResponseEntity<QueuePositionResponse> getPosition(
		@RequestHeader(value = "X-Queue-Token") String queueToken
	) {
		// 서명 토큰 인증 (DB 안 침)
		Long userId = queueAuthPort.resolveUserId(queueToken);

		// 순번 조회
		QueuePositionOutDto outDto = waitingQueueQueryFacade.getPosition(userId, queueToken);

		return ResponseEntity.ok(QueuePositionResponse.from(outDto));
	}
}
