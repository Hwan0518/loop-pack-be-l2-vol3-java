package com.loopers.payment.payment.domain.repository;


import com.loopers.payment.payment.domain.model.Payment;
import com.loopers.payment.payment.domain.model.enums.PaymentStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;


public interface PaymentQueryRepository {

	/**
	 * 결제 조회 리포지토리
	 * 1. ID로 결제 조회
	 * 2. 주문 ID + 결제 상태로 결제 조회
	 * 3. 트랜잭션 키로 결제 조회
	 * 4. 주문 ID로 REQUESTED 결제 조회
	 * 5. 주문 ID로 REQUESTED 결제 존재 여부 확인
	 * 6. 특정 시간 이전에 생성된 REQUESTED 결제 목록 조회
	 */

	// 1. ID로 결제 조회
	Optional<Payment> findById(Long id);

	// 2. 주문 ID + 결제 상태로 결제 조회
	Optional<Payment> findByOrderIdAndStatus(Long orderId, PaymentStatus status);

	// 3. 트랜잭션 키로 결제 조회
	Optional<Payment> findByTransactionKey(String transactionKey);

	// 4. 주문 ID로 REQUESTED 결제 조회
	Optional<Payment> findRequestedByOrderId(Long orderId);

	// 5. 주문 ID로 REQUESTED 결제 존재 여부 확인
	boolean existsRequestedByOrderId(Long orderId);

	// 6. 특정 시간 이전에 생성된 REQUESTED 결제 목록 조회
	List<Payment> findRequestedPaymentsCreatedBefore(LocalDateTime threshold);

}
