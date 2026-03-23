package com.loopers.payment.payment.application.port.out.client.pg.dto;


import java.util.List;


/**
 * PG 결제 응답 DTO (목록 — pgOrderId 기반 조회)
 * - meta: 응답 메타 (result, errorCode, message)
 * - data: 주문별 트랜잭션 목록 (orderId, transactions)
 */
public record PgPaymentListResponse(
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
		String orderId,
		List<Transaction> transactions
	) {
	}

	public record Transaction(
		String transactionKey,
		String cardType,
		String cardNo,
		Long amount,
		String status,
		String reason
	) {
	}

}
