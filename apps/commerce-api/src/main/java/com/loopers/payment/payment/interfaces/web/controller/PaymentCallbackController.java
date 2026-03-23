package com.loopers.payment.payment.interfaces.web.controller;


import com.loopers.payment.payment.application.facade.PaymentCommandFacade;
import com.loopers.payment.payment.interfaces.web.request.PaymentCallbackRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentCallbackController {

	// facade
	private final PaymentCommandFacade paymentCommandFacade;


	/**
	 * PG 콜백 컨트롤러 (인증 없음 — PG가 호출)
	 * 1. 결제 콜백 수신
	 */

	// 1. 결제 콜백 수신
	@PostMapping("/callback")
	public ResponseEntity<Void> handleCallback(@RequestBody PaymentCallbackRequest request) {

		// 콜백 처리 (멱등 — 중복 콜백 시 skip)
		paymentCommandFacade.handleCallback(
			request.transactionKey(),
			request.status(),
			request.reason()
		);

		return ResponseEntity.ok().build();
	}

}
