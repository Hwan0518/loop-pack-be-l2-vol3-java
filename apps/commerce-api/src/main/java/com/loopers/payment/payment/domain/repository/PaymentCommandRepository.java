package com.loopers.payment.payment.domain.repository;


import com.loopers.payment.payment.domain.model.Payment;


public interface PaymentCommandRepository {

	/**
	 * 결제 명령 리포지토리
	 * 1. 결제 저장
	 */

	// 1. 결제 저장
	Payment save(Payment payment);

}
