package com.loopers.queue.waitingqueue.interfaces.web.controller;


import com.loopers.queue.waitingqueue.application.dto.out.QueueEnterOutDto;
import com.loopers.queue.waitingqueue.application.dto.out.QueuePositionOutDto;
import com.loopers.queue.waitingqueue.application.facade.WaitingQueueCommandFacade;
import com.loopers.queue.waitingqueue.application.facade.WaitingQueueQueryFacade;
import com.loopers.queue.waitingqueue.interfaces.web.response.QueueEnterResponse;
import com.loopers.queue.waitingqueue.interfaces.web.response.QueuePositionResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/queue")
public class WaitingQueueController {

	// facade
	private final WaitingQueueCommandFacade waitingQueueCommandFacade;
	private final WaitingQueueQueryFacade waitingQueueQueryFacade;

	/**
	 * 대기열 컨트롤러
	 * 1. 대기열 진입
	 * 2. 순번 조회
	 */

	public WaitingQueueController(
		WaitingQueueCommandFacade waitingQueueCommandFacade,
		WaitingQueueQueryFacade waitingQueueQueryFacade
	) {
		this.waitingQueueCommandFacade = waitingQueueCommandFacade;
		this.waitingQueueQueryFacade = waitingQueueQueryFacade;
	}


	// 1. 대기열 진입 (서명 토큰 검증은 Service에서 수행)
	@PostMapping("/enter")
	public ResponseEntity<QueueEnterResponse> enter(
		@RequestHeader(value = "X-Queue-Token") String queueToken
	) {
		QueueEnterOutDto outDto = waitingQueueCommandFacade.enter(queueToken);
		return ResponseEntity.ok(QueueEnterResponse.from(outDto));
	}


	// 2. 순번 조회 (서명 토큰 검증은 Service에서 수행)
	@GetMapping("/position")
	public ResponseEntity<QueuePositionResponse> getPosition(
		@RequestHeader(value = "X-Queue-Token") String queueToken,
		@RequestHeader(value = "X-Enter-Receipt", required = false) String enterReceipt
	) {
		QueuePositionOutDto outDto = waitingQueueQueryFacade.getPosition(queueToken, enterReceipt);
		return ResponseEntity.ok(QueuePositionResponse.from(outDto));
	}
}
