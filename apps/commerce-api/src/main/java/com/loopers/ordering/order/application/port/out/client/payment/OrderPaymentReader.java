package com.loopers.ordering.order.application.port.out.client.payment;


/**
 * 주문용 결제 조회 포트 (Ordering BC → Payment BC)
 * 1. 주문 ID로 진행 중인 결제(REQUESTED) 존재 여부 확인
 */
public interface OrderPaymentReader {

	// 1. 주문 ID로 REQUESTED 결제 존재 여부 확인 (주문 만료 시 skip 판단)
	boolean existsRequestedPayment(Long orderId);

}
