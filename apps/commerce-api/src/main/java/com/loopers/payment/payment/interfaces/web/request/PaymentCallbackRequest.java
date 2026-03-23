package com.loopers.payment.payment.interfaces.web.request;


/**
 * PG 콜백 요청 (PG가 결제 결과 통보 시 전송)
 * - transactionKey: PG 트랜잭션 키
 * - orderId: PG 주문 ID
 * - cardType: 카드 타입
 * - cardNo: 카드번호
 * - amount: 결제 금액
 * - status: 결제 상태 (SUCCESS/FAILED)
 * - reason: 실패 사유
 */
public record PaymentCallbackRequest(
	String transactionKey,
	String orderId,
	String cardType,
	String cardNo,
	Long amount,
	String status,
	String reason
) {
}
