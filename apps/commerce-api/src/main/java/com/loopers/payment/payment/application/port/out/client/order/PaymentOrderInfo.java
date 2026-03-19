package com.loopers.payment.payment.application.port.out.client.order;


import java.math.BigDecimal;


/**
 * 결제용 주문 정보 VO (Payment BC에서 사용)
 * - orderId: 주문 ID
 * - userId: 주문자 ID
 * - totalPrice: 최종 결제 금액
 * - status: 주문 상태 (String — BC 경계를 넘지 않도록 Ordering BC의 enum 대신 문자열 사용)
 */
public record PaymentOrderInfo(
	Long orderId,
	Long userId,
	BigDecimal totalPrice,
	String status
) {

	// 결제 가능 상태 여부 (PENDING_PAYMENT 또는 PAYMENT_FAILED)
	public boolean isPayable() {
		return "PENDING_PAYMENT".equals(status) || "PAYMENT_FAILED".equals(status);
	}

	// 재결제 대상 여부 (PAYMENT_FAILED)
	public boolean isPaymentFailed() {
		return "PAYMENT_FAILED".equals(status);
	}

}
