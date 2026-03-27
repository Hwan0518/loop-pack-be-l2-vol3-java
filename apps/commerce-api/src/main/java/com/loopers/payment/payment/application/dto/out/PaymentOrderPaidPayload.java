package com.loopers.payment.payment.application.dto.out;


import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;


/**
 * Kafka 발행용 결제 완료 Payload — payment BC 소유 contract record
 * - Consumer BC(payment)가 소유하며, ORDER_PAID 이벤트의 Outbox 메시지 본문으로 사용
 */
public record PaymentOrderPaidPayload(
	Long orderId,
	Long userId,
	Long paymentId,
	List<PaymentOrderItemPayload> items,
	BigDecimal totalPrice,
	LocalDateTime occurredAt
) {
}
