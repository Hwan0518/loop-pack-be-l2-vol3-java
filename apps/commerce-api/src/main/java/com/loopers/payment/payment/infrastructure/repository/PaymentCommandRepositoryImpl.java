package com.loopers.payment.payment.infrastructure.repository;


import com.loopers.payment.payment.domain.model.Payment;
import com.loopers.payment.payment.domain.repository.PaymentCommandRepository;
import com.loopers.payment.payment.infrastructure.entity.PaymentEntity;
import com.loopers.payment.payment.infrastructure.jpa.PaymentJpaRepository;
import com.loopers.payment.payment.infrastructure.mapper.PaymentEntityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;


@Repository
@RequiredArgsConstructor
public class PaymentCommandRepositoryImpl implements PaymentCommandRepository {

	// jpa
	private final PaymentJpaRepository paymentJpaRepository;
	// mapper
	private final PaymentEntityMapper paymentMapper;


	/**
	 * 결제 명령 리포지토리 구현체
	 * 1. 결제 저장
	 */

	// 1. 결제 저장
	@Override
	public Payment save(Payment payment) {

		// 엔티티 변환 및 저장
		PaymentEntity entity = paymentMapper.toEntity(payment);
		PaymentEntity savedEntity = paymentJpaRepository.save(entity);

		// 도메인 변환 후 반환
		return paymentMapper.toDomain(savedEntity);
	}

}
