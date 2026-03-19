package com.loopers.payment.payment.application.scheduler;


import com.loopers.payment.payment.application.dto.out.PgRecoveryResult;
import com.loopers.payment.payment.application.service.PaymentCommandService;
import com.loopers.payment.payment.application.service.PaymentQueryService;
import com.loopers.payment.payment.domain.model.Payment;
import com.loopers.payment.payment.domain.model.enums.CardType;
import com.loopers.payment.payment.domain.model.enums.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentPollingScheduler 테스트")
class PaymentPollingSchedulerTest {

	@Mock
	private PaymentQueryService paymentQueryService;
	@Mock
	private PaymentCommandService paymentCommandService;

	private PaymentPollingScheduler scheduler;

	@BeforeEach
	void setUp() {
		scheduler = new PaymentPollingScheduler(paymentQueryService, paymentCommandService);
	}

	private Payment createPayment(Long id, String transactionKey) {
		return Payment.reconstruct(id, 100L, 1L, "PGO-test", CardType.SAMSUNG, "1234-5678-9012-3456",
			new BigDecimal("20000"), transactionKey, PaymentStatus.REQUESTED, null, LocalDateTime.now().minusSeconds(10));
	}


	@Nested
	@DisplayName("pollPaymentStatus() — transactionKey 있는 경우")
	class WithTransactionKeyTest {

		@Test
		@DisplayName("[폴링] transactionKey 있음 + PG SUCCESS -> updatePaymentResult 호출")
		void pollWithTxKeySuccess() {
			// Arrange
			Payment payment = createPayment(10L, "tx-123");
			given(paymentQueryService.findRequestedPaymentsCreatedBefore(5)).willReturn(List.of(payment));
			given(paymentCommandService.recoverPaymentStatus(payment)).willReturn(
				new PgRecoveryResult(true, "tx-123", "SUCCESS", "정상 승인", true)
			);

			// Act
			scheduler.pollPaymentStatus();

			// Assert
			verify(paymentCommandService).updatePaymentResult("tx-123", "SUCCESS", "정상 승인");
		}


		@Test
		@DisplayName("[폴링] transactionKey 있음 + PG PENDING -> skip (다음 주기)")
		void pollWithTxKeyPending() {
			// Arrange
			Payment payment = createPayment(10L, "tx-123");
			given(paymentQueryService.findRequestedPaymentsCreatedBefore(5)).willReturn(List.of(payment));
			given(paymentCommandService.recoverPaymentStatus(payment)).willReturn(
				new PgRecoveryResult(true, "tx-123", "PENDING", null, true)
			);

			// Act
			scheduler.pollPaymentStatus();

			// Assert -- updatePaymentResult 미호출
			verify(paymentCommandService, never()).updatePaymentResult(anyString(), anyString(), anyString());
		}

	}


	@Nested
	@DisplayName("pollPaymentStatus() — transactionKey 없는 경우")
	class WithoutTransactionKeyTest {

		@Test
		@DisplayName("[폴링] transactionKey 없음 + 매칭 트랜잭션 SUCCESS -> saveTransactionKey + updatePaymentResultByOrderId 호출")
		void pollWithoutTxKeyMatching() {
			// Arrange
			Payment payment = createPayment(10L, null);
			given(paymentQueryService.findRequestedPaymentsCreatedBefore(5)).willReturn(List.of(payment));
			given(paymentCommandService.recoverPaymentStatus(payment)).willReturn(
				new PgRecoveryResult(true, "tx-new", "SUCCESS", "정상 승인", false)
			);

			// Act
			scheduler.pollPaymentStatus();

			// Assert
			verify(paymentCommandService).saveTransactionKey(10L, "tx-new");
			verify(paymentCommandService).updatePaymentResultByOrderId(1L, "tx-new", "SUCCESS", "정상 승인");
		}


		@Test
		@DisplayName("[폴링] transactionKey 없음 + PG에 트랜잭션 없음 -> failPayment 호출")
		void pollWithoutTxKeyEmpty() {
			// Arrange
			Payment payment = createPayment(10L, null);
			given(paymentQueryService.findRequestedPaymentsCreatedBefore(5)).willReturn(List.of(payment));
			given(paymentCommandService.recoverPaymentStatus(payment)).willReturn(PgRecoveryResult.notFound());

			// Act
			scheduler.pollPaymentStatus();

			// Assert
			verify(paymentCommandService).failPayment(eq(10L), anyString());
		}


		@Test
		@DisplayName("[폴링] transactionKey 없음 + PG FAILED 결과 -> saveTransactionKey + updatePaymentResultByOrderId 호출")
		void pollWithoutTxKeyFailed() {
			// Arrange
			Payment payment = createPayment(10L, null);
			given(paymentQueryService.findRequestedPaymentsCreatedBefore(5)).willReturn(List.of(payment));
			given(paymentCommandService.recoverPaymentStatus(payment)).willReturn(
				new PgRecoveryResult(true, "tx-fail", "FAILED", "한도초과", false)
			);

			// Act
			scheduler.pollPaymentStatus();

			// Assert -- pgOrderId 유일이므로 첫 번째 트랜잭션으로 결과 반영
			verify(paymentCommandService).saveTransactionKey(10L, "tx-fail");
			verify(paymentCommandService).updatePaymentResultByOrderId(1L, "tx-fail", "FAILED", "한도초과");
		}


		@Test
		@DisplayName("[폴링] transactionKey 없음 + 매칭 PENDING -> transactionKey 저장 + skip")
		void pollWithoutTxKeyPending() {
			// Arrange
			Payment payment = createPayment(10L, null);
			given(paymentQueryService.findRequestedPaymentsCreatedBefore(5)).willReturn(List.of(payment));
			given(paymentCommandService.recoverPaymentStatus(payment)).willReturn(
				new PgRecoveryResult(true, "tx-pending", "PENDING", null, false)
			);

			// Act
			scheduler.pollPaymentStatus();

			// Assert -- transactionKey 저장 but updatePaymentResult 미호출
			verify(paymentCommandService).saveTransactionKey(10L, "tx-pending");
			verify(paymentCommandService, never()).updatePaymentResultByOrderId(anyLong(), anyString(), anyString(), anyString());
		}

	}


	@Nested
	@DisplayName("pollPaymentStatus() — 에러 처리")
	class ErrorHandlingTest {

		@Test
		@DisplayName("[폴링] PG 조회 실패 -> skip + 다른 건 정상 처리")
		void pollPartialFailure() {
			// Arrange
			Payment failPayment = createPayment(10L, "tx-fail");
			Payment okPayment = createPayment(20L, "tx-ok");
			given(paymentQueryService.findRequestedPaymentsCreatedBefore(5)).willReturn(List.of(failPayment, okPayment));
			given(paymentCommandService.recoverPaymentStatus(failPayment))
				.willThrow(new RuntimeException("PG 연결 실패"));
			given(paymentCommandService.recoverPaymentStatus(okPayment)).willReturn(
				new PgRecoveryResult(true, "tx-ok", "SUCCESS", "정상", true)
			);

			// Act
			scheduler.pollPaymentStatus();

			// Assert -- 실패 건은 skip, 성공 건은 처리
			verify(paymentCommandService).updatePaymentResult("tx-ok", "SUCCESS", "정상");
		}


		@Test
		@DisplayName("[폴링] 대상 없음 -> 아무 처리 없음")
		void pollNoTarget() {
			// Arrange
			given(paymentQueryService.findRequestedPaymentsCreatedBefore(5)).willReturn(List.of());

			// Act
			scheduler.pollPaymentStatus();

			// Assert
			verifyNoInteractions(paymentCommandService);
		}

	}

}
