package com.loopers.payment.payment.application.port.out.client.order;


import java.util.List;


/**
 * 결제용 주문 조회 포트 (Payment BC → Order BC)
 * 1. 결제용 주문 조회 (비관적 락 + 사용자 검증)
 * 2. 주문 항목 조회 (ORDER_PAID payload용)
 */
public interface PaymentOrderReader {

	// 1. 결제용 주문 조회 (비관적 락 + 사용자 검증)
	PaymentOrderInfo findOrderForPayment(Long orderId, Long userId);

	// 2. 주문 항목 조회 (ORDER_PAID payload용 — sales_count 집계에 필요)
	List<PaymentOrderItemInfo> findOrderItems(Long orderId);

}
