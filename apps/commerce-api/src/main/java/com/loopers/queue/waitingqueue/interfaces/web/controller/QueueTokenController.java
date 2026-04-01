package com.loopers.queue.waitingqueue.interfaces.web.controller;


import com.loopers.queue.waitingqueue.application.dto.out.QueueTokenOutDto;
import com.loopers.queue.waitingqueue.application.facade.QueueTokenCommandFacade;
import com.loopers.queue.waitingqueue.interfaces.web.response.QueueTokenResponse;
import com.loopers.support.common.auth.AuthenticationResolver;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
@RestController
public class QueueTokenController {

	// facade
	private final QueueTokenCommandFacade queueTokenCommandFacade;

	// auth
	private final AuthenticationResolver authenticationResolver;

	/**
	 * 대기열 토큰 발급 컨트롤러
	 * 1. 대기열 전용 서명 토큰 발급
	 */

	public QueueTokenController(
		QueueTokenCommandFacade queueTokenCommandFacade,
		AuthenticationResolver authenticationResolver
	) {
		this.queueTokenCommandFacade = queueTokenCommandFacade;
		this.authenticationResolver = authenticationResolver;
	}


	// 1. 대기열 전용 서명 토큰 발급 (DB 인증 1회 → 서명 토큰)
	@PostMapping("/auth/queue-token")
	public ResponseEntity<QueueTokenResponse> issueQueueToken(
		@RequestHeader(value = "X-Loopers-LoginId", required = false) String loginId,
		@RequestHeader(value = "X-Loopers-LoginPw", required = false) String password
	) {
		// DB 인증 (1회)
		Long userId = authenticationResolver.resolve(loginId, password);

		// 서명 토큰 발급
		QueueTokenOutDto outDto = queueTokenCommandFacade.issueToken(userId);

		return ResponseEntity.ok(QueueTokenResponse.from(outDto));
	}
}
