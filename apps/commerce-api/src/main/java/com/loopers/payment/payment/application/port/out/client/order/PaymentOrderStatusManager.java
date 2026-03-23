package com.loopers.payment.payment.application.port.out.client.order;


/**
 * 결제용 주문 상태 관리 포트 (Payment BC → Order BC)
 * 1. 주문 상태를 PAID로 변경
 * 2. 주문 상태를 PAYMENT_FAILED로 변경
 * 3. 주문 상태를 PENDING_PAYMENT로 변경 (재결제 시)
 */
public interface PaymentOrderStatusManager {

	// 1. 주문 상태를 PAID로 변경
	void markOrderPaid(Long orderId);

	// 2. 주문 상태를 PAYMENT_FAILED로 변경
	void markOrderPaymentFailed(Long orderId);

	// 3. 주문 상태를 PENDING_PAYMENT로 변경 (재결제 시)
	void markOrderPendingPayment(Long orderId);

}
