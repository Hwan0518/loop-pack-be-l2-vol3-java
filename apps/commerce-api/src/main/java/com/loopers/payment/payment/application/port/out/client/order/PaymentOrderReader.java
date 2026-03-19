package com.loopers.payment.payment.application.port.out.client.order;


/**
 * 결제용 주문 조회 포트 (Payment BC → Order BC)
 * 1. 결제용 주문 조회 (비관적 락 + 사용자 검증)
 */
public interface PaymentOrderReader {

	// 1. 결제용 주문 조회 (비관적 락 + 사용자 검증)
	PaymentOrderInfo findOrderForPayment(Long orderId, Long userId);

}
