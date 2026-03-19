package com.loopers.payment.payment.infrastructure.mapper;


import com.loopers.payment.payment.domain.model.Payment;
import com.loopers.payment.payment.infrastructure.entity.PaymentEntity;
import org.springframework.stereotype.Component;


/**
 * 결제 엔티티 매퍼
 * 1. Domain -> Entity 변환
 * 2. Entity -> Domain 변환
 */
@Component
public class PaymentEntityMapper {

	// 1. Domain -> Entity 변환
	public PaymentEntity toEntity(Payment payment) {
		return PaymentEntity.of(
			payment.getId(),
			payment.getUserId(),
			payment.getOrderId(),
			payment.getPgOrderId(),
			payment.getCardType(),
			payment.getCardNo(),
			payment.getAmount(),
			payment.getTransactionKey(),
			payment.getStatus(),
			payment.getFailureReason()
		);
	}


	// 2. Entity -> Domain 변환
	public Payment toDomain(PaymentEntity entity) {
		return Payment.reconstruct(
			entity.getId(),
			entity.getUserId(),
			entity.getOrderId(),
			entity.getPgOrderId(),
			entity.getCardType(),
			entity.getCardNo(),
			entity.getAmount(),
			entity.getTransactionKey(),
			entity.getStatus(),
			entity.getFailureReason(),
			entity.getCreatedAt() != null ? entity.getCreatedAt().toLocalDateTime() : null
		);
	}

}
