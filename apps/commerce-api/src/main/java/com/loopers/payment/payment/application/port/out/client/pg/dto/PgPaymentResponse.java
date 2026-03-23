package com.loopers.payment.payment.application.port.out.client.pg.dto;


/**
 * PG 결제 응답 DTO (단건)
 * - meta: 응답 메타 (result, errorCode, message)
 * - data: 결제 데이터 (transactionKey, orderId, cardType, cardNo, amount, status, reason)
 */
public record PgPaymentResponse(
	Meta meta,
	Data data
) {

	public record Meta(
		String result,
		String errorCode,
		String message
	) {
	}

	public record Data(
		String transactionKey,
		String orderId,
		String cardType,
		String cardNo,
		Long amount,
		String status,
		String reason
	) {
	}

}
