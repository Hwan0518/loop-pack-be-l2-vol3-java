package com.loopers.payment.payment.interfaces.web.controller;


import com.loopers.payment.payment.application.dto.in.PaymentCreateInDto;
import com.loopers.payment.payment.application.dto.out.PaymentOutDto;
import com.loopers.payment.payment.application.facade.PaymentCommandFacade;
import com.loopers.payment.payment.interfaces.web.request.PaymentCreateRequest;
import com.loopers.payment.payment.interfaces.web.response.PaymentResponse;
import com.loopers.support.common.auth.AuthenticationResolver;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

	// facade
	private final PaymentCommandFacade paymentCommandFacade;
	// auth
	private final AuthenticationResolver authenticationResolver;


	/**
	 * 결제 컨트롤러 (사용자 인증 필요)
	 * 1. 결제 요청
	 */

	// 1. 결제 요청
	@PostMapping
	public ResponseEntity<PaymentResponse> requestPayment(
		@RequestHeader(value = "X-Loopers-LoginId", required = false) String loginId,
		@RequestHeader(value = "X-Loopers-LoginPw", required = false) String password,
		@RequestBody @Valid PaymentCreateRequest request
	) {

		// 사용자 인증
		Long userId = authenticationResolver.resolve(loginId, password);

		// 결제 요청
		PaymentCreateInDto inDto = new PaymentCreateInDto(request.orderId(), request.cardType(), request.cardNo());
		PaymentOutDto outDto = paymentCommandFacade.requestPayment(userId, inDto);

		return ResponseEntity.status(HttpStatus.CREATED).body(PaymentResponse.from(outDto));
	}

}
