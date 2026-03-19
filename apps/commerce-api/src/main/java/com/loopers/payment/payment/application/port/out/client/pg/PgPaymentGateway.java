package com.loopers.payment.payment.application.port.out.client.pg;


import com.loopers.payment.payment.application.port.out.client.pg.dto.PgPaymentListResponse;
import com.loopers.payment.payment.application.port.out.client.pg.dto.PgPaymentRequest;
import com.loopers.payment.payment.application.port.out.client.pg.dto.PgPaymentResponse;


/**
 * PG 결제 게이트웨이 포트 (Payment BC → PG)
 * 1. 결제 요청
 * 2. 트랜잭션 키로 결제 조회
 * 3. PG 주문 ID로 결제 목록 조회
 */
public interface PgPaymentGateway {

	// 1. 결제 요청
	PgPaymentResponse requestPayment(Long userId, PgPaymentRequest request);

	// 2. 트랜잭션 키로 결제 조회
	PgPaymentResponse getPaymentByTransactionKey(Long userId, String transactionKey);

	// 3. PG 주문 ID로 결제 목록 조회 (pgOrderId — 결제 시도 단위 유일 식별자)
	PgPaymentListResponse getPaymentsByOrderId(Long userId, String pgOrderId);

}
