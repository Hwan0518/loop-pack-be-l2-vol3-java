package com.loopers.payment.payment.infrastructure.jpa;


import com.loopers.payment.payment.domain.model.enums.PaymentStatus;
import com.loopers.payment.payment.infrastructure.entity.PaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;


public interface PaymentJpaRepository extends JpaRepository<PaymentEntity, Long> {

	/**
	 * 결제 JPA 리포지토리
	 * 1. 주문 ID + 결제 상태로 결제 조회
	 * 2. 트랜잭션 키로 결제 조회
	 * 3. 주문 ID + 결제 상태로 결제 존재 여부 확인
	 * 4. 결제 상태 + 생성일시 기준 조회
	 */

	// 1. 주문 ID + 결제 상태로 결제 조회
	Optional<PaymentEntity> findByOrderIdAndStatus(Long orderId, PaymentStatus status);

	// 2. 트랜잭션 키로 결제 조회
	Optional<PaymentEntity> findByTransactionKey(String transactionKey);

	// 3. 주문 ID + 결제 상태로 결제 존재 여부 확인
	boolean existsByOrderIdAndStatus(Long orderId, PaymentStatus status);

	// 4. 결제 상태 + 생성일시 기준 조회
	List<PaymentEntity> findByStatusAndCreatedAtBefore(PaymentStatus status, ZonedDateTime threshold);

}
