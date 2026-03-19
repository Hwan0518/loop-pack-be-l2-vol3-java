package com.loopers.payment.payment.application.dto.out;


import com.loopers.payment.payment.domain.model.Payment;
import com.loopers.payment.payment.domain.model.enums.CardType;
import com.loopers.payment.payment.domain.model.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;


/**
 * 결제 출력 DTO
 * - id: 결제 ID
 * - userId: 결제 요청 사용자 ID
 * - orderId: 주문 ID
 * - pgOrderId: PG 전용 주문 식별자
 * - cardType: 카드 타입
 * - cardNo: 카드번호
 * - amount: 결제 금액
 * - transactionKey: PG 트랜잭션 키
 * - status: 결제 상태
 * - failureReason: 결제 실패 사유
 * - createdAt: 결제 생성 일시
 */
public record PaymentOutDto(
	Long id,
	Long userId,
	Long orderId,
	String pgOrderId,
	CardType cardType,
	String cardNo,
	BigDecimal amount,
	String transactionKey,
	PaymentStatus status,
	String failureReason,
	LocalDateTime createdAt
) {

	public static PaymentOutDto from(Payment payment) {
		return new PaymentOutDto(
			payment.getId(),
			payment.getUserId(),
			payment.getOrderId(),
			payment.getPgOrderId(),
			payment.getCardType(),
			payment.getCardNo(),
			payment.getAmount(),
			payment.getTransactionKey(),
			payment.getStatus(),
			payment.getFailureReason(),
			payment.getCreatedAt()
		);
	}

}
