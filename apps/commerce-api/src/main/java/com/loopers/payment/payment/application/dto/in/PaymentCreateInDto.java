package com.loopers.payment.payment.application.dto.in;


/**
 * 결제 생성 입력 DTO
 * - orderId: 주문 ID
 * - cardType: 카드 타입 (문자열)
 * - cardNo: 카드번호 (XXXX-XXXX-XXXX-XXXX)
 */
public record PaymentCreateInDto(
	Long orderId,
	String cardType,
	String cardNo
) {
}
