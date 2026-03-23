package com.loopers.payment.payment.application.service;


import com.loopers.payment.payment.domain.model.Payment;
import com.loopers.payment.payment.domain.model.enums.PaymentStatus;
import com.loopers.payment.payment.domain.repository.PaymentQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;


@Service
@RequiredArgsConstructor
public class PaymentQueryService {

	// repository
	private final PaymentQueryRepository paymentQueryRepository;


	/**
	 * 결제 조회 서비스
	 * 1. 주문 ID + 결제 상태로 조회 (멱등성 체크)
	 * 2. 폴링 대상 조회 (REQUESTED + 특정 시간 이전)
	 * 3. 주문 ID로 REQUESTED 결제 존재 여부 확인 (주문 만료 시 확인)
	 * 4. 트랜잭션 키로 결제 조회 (콜백 수신 시)
	 * 5. ID로 결제 조회
	 * 6. 주문 ID로 REQUESTED 결제 조회
	 */

	// 1. 주문 ID + 결제 상태로 조회 (멱등성 체크)
	@Transactional(readOnly = true)
	public Optional<Payment> findByOrderIdAndStatus(Long orderId, PaymentStatus status) {
		return paymentQueryRepository.findByOrderIdAndStatus(orderId, status);
	}


	// 2. 폴링 대상 조회 (REQUESTED + 특정 시간 이전 생성)
	@Transactional(readOnly = true)
	public List<Payment> findRequestedPaymentsCreatedBefore(int seconds) {
		LocalDateTime threshold = LocalDateTime.now().minusSeconds(seconds);
		return paymentQueryRepository.findRequestedPaymentsCreatedBefore(threshold);
	}


	// 3. 주문 ID로 REQUESTED 결제 존재 여부 확인
	@Transactional(readOnly = true)
	public boolean existsRequestedByOrderId(Long orderId) {
		return paymentQueryRepository.existsRequestedByOrderId(orderId);
	}


	// 4. 트랜잭션 키로 결제 조회 (콜백 수신 시)
	@Transactional(readOnly = true)
	public Optional<Payment> findByTransactionKey(String transactionKey) {
		return paymentQueryRepository.findByTransactionKey(transactionKey);
	}


	// 5. ID로 결제 조회
	@Transactional(readOnly = true)
	public Optional<Payment> findById(Long id) {
		return paymentQueryRepository.findById(id);
	}


	// 6. 주문 ID로 REQUESTED 결제 조회
	@Transactional(readOnly = true)
	public Optional<Payment> findRequestedByOrderId(Long orderId) {
		return paymentQueryRepository.findRequestedByOrderId(orderId);
	}

}
