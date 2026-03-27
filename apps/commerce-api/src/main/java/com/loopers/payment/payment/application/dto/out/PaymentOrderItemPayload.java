package com.loopers.payment.payment.application.dto.out;


/**
 * Kafka 발행용 결제 완료 주문 항목 Payload — payment BC 소유 contract record
 * - Consumer BC(payment)가 소유하며, 주문 항목 정보를 ORDER_PAID 이벤트에 포함
 */
public record PaymentOrderItemPayload(
	Long productId,
	Long quantity
) {
}
