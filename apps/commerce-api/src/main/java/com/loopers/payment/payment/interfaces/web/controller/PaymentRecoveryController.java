package com.loopers.payment.payment.interfaces.web.controller;


import com.loopers.payment.payment.application.dto.out.PaymentOutDto;
import com.loopers.payment.payment.application.facade.PaymentCommandFacade;
import com.loopers.payment.payment.interfaces.web.response.PaymentResponse;
import com.loopers.support.common.AdminHeaderValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api-admin/v1/payments")
@RequiredArgsConstructor
public class PaymentRecoveryController {

	// facade
	private final PaymentCommandFacade paymentCommandFacade;


	/**
	 * 관리자 결제 수동 복구 컨트롤러 (LDAP 인증 필요)
	 * 1. 결제 수동 복구 (PG 조회 → 상태 반영)
	 */

	// 1. 결제 수동 복구
	@PostMapping("/{paymentId}/recover")
	public ResponseEntity<PaymentResponse> recoverPayment(
		@RequestHeader(value = "X-Loopers-Ldap", required = false) String ldapHeader,
		@PathVariable Long paymentId
	) {

		// LDAP 인증
		AdminHeaderValidator.validate(ldapHeader);

		// 수동 복구
		PaymentOutDto outDto = paymentCommandFacade.recoverPayment(paymentId);

		return ResponseEntity.ok(PaymentResponse.from(outDto));
	}

}
