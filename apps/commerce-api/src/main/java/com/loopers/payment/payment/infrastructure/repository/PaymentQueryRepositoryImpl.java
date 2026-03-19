package com.loopers.payment.payment.infrastructure.repository;


import com.loopers.payment.payment.domain.model.Payment;
import com.loopers.payment.payment.domain.model.enums.PaymentStatus;
import com.loopers.payment.payment.domain.repository.PaymentQueryRepository;
import com.loopers.payment.payment.infrastructure.jpa.PaymentJpaRepository;
import com.loopers.payment.payment.infrastructure.mapper.PaymentEntityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;


@Repository
@RequiredArgsConstructor
public class PaymentQueryRepositoryImpl implements PaymentQueryRepository {

	// jpa
	private final PaymentJpaRepository paymentJpaRepository;
	// mapper
	private final PaymentEntityMapper paymentMapper;


	/**
	 * 결제 조회 리포지토리 구현체
	 * 1. ID로 결제 조회
	 * 2. 주문 ID + 결제 상태로 결제 조회
	 * 3. 트랜잭션 키로 결제 조회
	 * 4. 주문 ID로 REQUESTED 결제 조회
	 * 5. 주문 ID로 REQUESTED 결제 존재 여부 확인
	 * 6. 특정 시간 이전에 생성된 REQUESTED 결제 목록 조회
	 */

	// 1. ID로 결제 조회
	@Override
	public Optional<Payment> findById(Long id) {
		return paymentJpaRepository.findById(id)
			.map(paymentMapper::toDomain);
	}


	// 2. 주문 ID + 결제 상태로 결제 조회
	@Override
	public Optional<Payment> findByOrderIdAndStatus(Long orderId, PaymentStatus status) {
		return paymentJpaRepository.findByOrderIdAndStatus(orderId, status)
			.map(paymentMapper::toDomain);
	}


	// 3. 트랜잭션 키로 결제 조회
	@Override
	public Optional<Payment> findByTransactionKey(String transactionKey) {
		return paymentJpaRepository.findByTransactionKey(transactionKey)
			.map(paymentMapper::toDomain);
	}


	// 4. 주문 ID로 REQUESTED 결제 조회
	@Override
	public Optional<Payment> findRequestedByOrderId(Long orderId) {
		return findByOrderIdAndStatus(orderId, PaymentStatus.REQUESTED);
	}


	// 5. 주문 ID로 REQUESTED 결제 존재 여부 확인
	@Override
	public boolean existsRequestedByOrderId(Long orderId) {
		return paymentJpaRepository.existsByOrderIdAndStatus(orderId, PaymentStatus.REQUESTED);
	}


	// 6. 특정 시간 이전에 생성된 REQUESTED 결제 목록 조회
	@Override
	public List<Payment> findRequestedPaymentsCreatedBefore(LocalDateTime threshold) {

		// LocalDateTime -> ZonedDateTime 변환
		ZonedDateTime zonedThreshold = threshold.atZone(ZoneId.systemDefault());

		return paymentJpaRepository.findByStatusAndCreatedAtBefore(PaymentStatus.REQUESTED, zonedThreshold)
			.stream()
			.map(paymentMapper::toDomain)
			.toList();
	}

}
