package com.loopers.payment.payment.application.scheduler;


import com.loopers.payment.payment.application.dto.out.PgRecoveryResult;
import com.loopers.payment.payment.application.service.PaymentCommandService;
import com.loopers.payment.payment.application.service.PaymentQueryService;
import com.loopers.payment.payment.domain.model.Payment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;


@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentPollingScheduler {

	// service
	private final PaymentQueryService paymentQueryService;
	private final PaymentCommandService paymentCommandService;


	/**
	 * 결제 상태 폴링 스케줄러
	 * - 10초 주기로 REQUESTED 상태의 결제를 PG에 조회하여 상태 반영
	 * - 5초 이상 경과한 REQUESTED 결제만 대상 (PG 처리 시간 고려)
	 * 1. 결제 상태 폴링 (10초 주기)
	 */

	// 1. 결제 상태 폴링 (10초 주기)
	@Scheduled(fixedDelay = 10000)
	public void pollPaymentStatus() {

		// 5초 이상 경과한 REQUESTED 결제 조회
		List<Payment> pendingPayments = paymentQueryService.findRequestedPaymentsCreatedBefore(5);

		// 건별 처리 (한 건 실패가 다른 건에 영향 X)
		for (Payment payment : pendingPayments) {
			try {
				processPayment(payment);
			} catch (Exception e) {
				// PG 조회 실패 → skip + 에러 로그 (다음 주기에 재시도)
				log.error("[PaymentPolling] paymentId={} 폴링 실패 — 예외 타입: {}, 메시지: {}",
					payment.getId(), e.getClass().getSimpleName(), e.getMessage(), e);
			}
		}
	}


	/**
	 * private method
	 * - 개별 결제 PG 조회 + 상태 반영 (PgRecoveryResult 기반)
	 */

	// 개별 결제 PG 조회 + 상태 반영
	private void processPayment(Payment payment) {

		// PG 조회 (Service가 infrastructure DTO를 도메인 DTO로 변환)
		PgRecoveryResult result = paymentCommandService.recoverPaymentStatus(payment);

		if (!result.found()) {
			// PG에 트랜잭션 없음 → 요청 미도달로 판단 → FAILED
			paymentCommandService.failPayment(payment.getId(), "PG에 트랜잭션이 존재하지 않습니다.");
			return;
		}

		// pgOrderId 기반 조회에서 transactionKey 획득 → 저장 (다음 폴링에서 단건 조회 가능하도록)
		if (!result.byTransactionKey() && result.transactionKey() != null && !result.transactionKey().isBlank()) {
			paymentCommandService.saveTransactionKey(payment.getId(), result.transactionKey());
		}

		// PENDING → skip (다음 주기에 재조회)
		if ("PENDING".equals(result.status())) {
			return;
		}

		// 결제 결과 반영
		if (result.byTransactionKey()) {
			// transactionKey 기반 결과 반영
			paymentCommandService.updatePaymentResult(result.transactionKey(), result.status(), result.reason());
		} else {
			// orderId 기반 결과 반영
			paymentCommandService.updatePaymentResultByOrderId(
				payment.getOrderId(), result.transactionKey(), result.status(), result.reason()
			);
		}
	}

}
