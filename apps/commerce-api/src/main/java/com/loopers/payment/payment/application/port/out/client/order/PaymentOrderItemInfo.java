package com.loopers.payment.payment.application.port.out.client.order;


/**
 * 결제 이벤트용 주문 항목 정보 (Payment BC → Order BC)
 * - ORDER_PAID 이벤트 payload에 포함되어 sales_count 집계에 사용
 */
public record PaymentOrderItemInfo(
	Long productId,
	Long quantity
) {
}
