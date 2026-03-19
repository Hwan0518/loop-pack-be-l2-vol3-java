package com.loopers.payment.payment.application.port.out.client.pg.dto;


/**
 * PG 결제 요청 DTO
 * - orderId: PG 주문 ID (pgOrderId)
 * - cardType: 카드 타입
 * - cardNo: 카드번호
 * - amount: 결제 금액
 * - callbackUrl: 콜백 URL
 */
public record PgPaymentRequest(
	String orderId,
	String cardType,
	String cardNo,
	Long amount,
	String callbackUrl
) {
}
