package com.loopers.payment.payment.interfaces.web.request;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;


/**
 * 결제 요청
 * - orderId: 주문 ID
 * - cardType: 카드 타입 (SAMSUNG, KB, HYUNDAI)
 * - cardNo: 카드번호 (XXXX-XXXX-XXXX-XXXX)
 */
public record PaymentCreateRequest(
	@NotNull Long orderId,
	@NotBlank String cardType,
	@NotBlank String cardNo
) {
}
