package com.loopers.payment.payment.application.facade;


import com.loopers.payment.payment.application.dto.in.PaymentCreateInDto;
import com.loopers.payment.payment.application.dto.out.PaymentOutDto;
import com.loopers.payment.payment.application.service.PaymentCommandService;
import com.loopers.payment.payment.application.service.PaymentQueryService;
import com.loopers.payment.payment.domain.model.Payment;
import com.loopers.payment.payment.domain.model.enums.CardType;
import com.loopers.payment.payment.domain.model.enums.PaymentStatus;
import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;


@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentCommandFacade 테스트")
class PaymentCommandFacadeTest {

	@Mock
	private PaymentCommandService paymentCommandService;
	@Mock
	private PaymentQueryService paymentQueryService;

	private PaymentCommandFacade paymentCommandFacade;

	@BeforeEach
	void setUp() {
		paymentCommandFacade = new PaymentCommandFacade(paymentCommandService, paymentQueryService);
	}

	private Payment createTestPayment(Long id, PaymentStatus status) {
		return Payment.reconstruct(id, 100L, 1L, "PGO-test", CardType.SAMSUNG, "1234-5678-9012-3456",
			new BigDecimal("20000"), "tx-123", status, null, LocalDateTime.now());
	}


	@Nested
	@DisplayName("requestPayment() 테스트")
	class RequestPaymentTest {

		@Test
		@DisplayName("[requestPayment()] PG 성공 -> 201 PaymentOutDto 반환. transactionKey 저장 + in-memory 반영")
		void requestPaymentSuccess() {
			// Arrange
			Payment payment = createTestPayment(10L, PaymentStatus.REQUESTED);
			given(paymentCommandService.createPaymentWithLock(100L, 1L, "SAMSUNG", "1234-5678-9012-3456"))
				.willReturn(payment);
			given(paymentCommandService.requestPaymentAndGetTransactionKey(eq(100L), any(String.class), any(), any(), any()))
				.willReturn("tx-new-123");

			// Act
			PaymentOutDto result = paymentCommandFacade.requestPayment(100L,
				new PaymentCreateInDto(1L, "SAMSUNG", "1234-5678-9012-3456"));

			// Assert
			assertAll(
				() -> assertThat(result.id()).isEqualTo(10L),
				() -> verify(paymentCommandService).saveTransactionKey(10L, "tx-new-123")
			);
		}


		@Test
		@DisplayName("[requestPayment()] Read timeout (null transactionKey) -> PG_TIMEOUT 예외. Payment REQUESTED 유지")
		void requestPaymentReadTimeout() {
			// Arrange
			Payment payment = createTestPayment(10L, PaymentStatus.REQUESTED);
			given(paymentCommandService.createPaymentWithLock(100L, 1L, "SAMSUNG", "1234-5678-9012-3456"))
				.willReturn(payment);
			given(paymentCommandService.requestPaymentAndGetTransactionKey(eq(100L), any(String.class), any(), any(), any()))
				.willReturn(null);

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> paymentCommandFacade.requestPayment(100L,
					new PaymentCreateInDto(1L, "SAMSUNG", "1234-5678-9012-3456")));

			// Assert — 504 PG_TIMEOUT, failPayment 미호출
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.PG_TIMEOUT),
				() -> verify(paymentCommandService, never()).failPayment(anyLong(), anyString())
			);
		}


		@Test
		@DisplayName("[requestPayment()] PG 실패 예외 -> PG_REQUEST_FAILED (502). Payment FAILED + Order PAYMENT_FAILED")
		void requestPaymentPgFailure() {
			// Arrange
			Payment payment = createTestPayment(10L, PaymentStatus.REQUESTED);
			given(paymentCommandService.createPaymentWithLock(100L, 1L, "SAMSUNG", "1234-5678-9012-3456"))
				.willReturn(payment);
			given(paymentCommandService.requestPaymentAndGetTransactionKey(eq(100L), any(String.class), any(), any(), any()))
				.willThrow(new CoreException(ErrorType.PG_REQUEST_FAILED));

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> paymentCommandFacade.requestPayment(100L,
					new PaymentCreateInDto(1L, "SAMSUNG", "1234-5678-9012-3456")));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.PG_REQUEST_FAILED),
				() -> verify(paymentCommandService).failPayment(eq(10L), anyString())
			);
		}


		@Test
		@DisplayName("[requestPayment()] Connection timeout -> PG_TIMEOUT (504). Payment FAILED + Order PAYMENT_FAILED")
		void requestPaymentConnectionTimeout() {
			// Arrange
			Payment payment = createTestPayment(10L, PaymentStatus.REQUESTED);
			given(paymentCommandService.createPaymentWithLock(100L, 1L, "SAMSUNG", "1234-5678-9012-3456"))
				.willReturn(payment);
			given(paymentCommandService.requestPaymentAndGetTransactionKey(eq(100L), any(String.class), any(), any(), any()))
				.willThrow(new CoreException(ErrorType.PG_TIMEOUT));

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> paymentCommandFacade.requestPayment(100L,
					new PaymentCreateInDto(1L, "SAMSUNG", "1234-5678-9012-3456")));

			// Assert — 504 + failPayment 호출
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.PG_TIMEOUT),
				() -> verify(paymentCommandService).failPayment(eq(10L), anyString())
			);
		}


		@Test
		@DisplayName("[requestPayment()] 서킷 OPEN -> PG_SERVICE_UNAVAILABLE (503). Payment REQUESTED 유지, failPayment 미호출")
		void requestPaymentCircuitOpen() {
			// Arrange
			Payment payment = createTestPayment(10L, PaymentStatus.REQUESTED);
			given(paymentCommandService.createPaymentWithLock(100L, 1L, "SAMSUNG", "1234-5678-9012-3456"))
				.willReturn(payment);
			given(paymentCommandService.requestPaymentAndGetTransactionKey(eq(100L), any(String.class), any(), any(), any()))
				.willThrow(new CoreException(ErrorType.PG_SERVICE_UNAVAILABLE));

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> paymentCommandFacade.requestPayment(100L,
					new PaymentCreateInDto(1L, "SAMSUNG", "1234-5678-9012-3456")));

			// Assert — 503 + failPayment 미호출 (PG 호출 자체 안 했으므로 REQUESTED 유지)
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.PG_SERVICE_UNAVAILABLE),
				() -> verify(paymentCommandService, never()).failPayment(anyLong(), anyString())
			);
		}

	}


	@Nested
	@DisplayName("requestPayment() — 예상치 못한 예외")
	class RequestPaymentUnexpectedExceptionTest {

		@Test
		@DisplayName("[requestPayment()] 예상치 못한 RuntimeException -> PG_REQUEST_FAILED (502). failPayment 호출")
		void requestPaymentUnexpectedException() {
			// Arrange
			Payment payment = createTestPayment(10L, PaymentStatus.REQUESTED);
			given(paymentCommandService.createPaymentWithLock(100L, 1L, "SAMSUNG", "1234-5678-9012-3456"))
				.willReturn(payment);
			given(paymentCommandService.requestPaymentAndGetTransactionKey(eq(100L), any(String.class), any(), any(), any()))
				.willThrow(new RuntimeException("예상치 못한 오류"));

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> paymentCommandFacade.requestPayment(100L,
					new PaymentCreateInDto(1L, "SAMSUNG", "1234-5678-9012-3456")));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.PG_REQUEST_FAILED),
				() -> verify(paymentCommandService).failPayment(eq(10L), anyString())
			);
		}

	}


	@Nested
	@DisplayName("recoverPayment() 테스트")
	class RecoverPaymentTest {

		@Test
		@DisplayName("[recoverPayment()] 이미 SUCCESS -> skip. 현재 상태 그대로 반환")
		void recoverAlreadySuccess() {
			// Arrange
			Payment payment = createTestPayment(10L, PaymentStatus.SUCCESS);
			given(paymentQueryService.findById(10L)).willReturn(Optional.of(payment));

			// Act
			PaymentOutDto result = paymentCommandFacade.recoverPayment(10L);

			// Assert
			assertAll(
				() -> assertThat(result.id()).isEqualTo(10L),
				() -> verify(paymentCommandService, never()).recoverPaymentStatus(any())
			);
		}


		@Test
		@DisplayName("[recoverPayment()] Read timeout 직후 PG 미등록 -> grace 기간 동안 REQUESTED 유지")
		void recoverNotFoundInPgWithinGracePeriod() {
			// Arrange
			Payment payment = Payment.reconstruct(10L, 100L, 1L, "PGO-test", CardType.SAMSUNG, "1234-5678-9012-3456",
				new BigDecimal("20000"), null, PaymentStatus.REQUESTED, null, LocalDateTime.now());
			given(paymentQueryService.findById(10L)).willReturn(Optional.of(payment));
			given(paymentCommandService.recoverPaymentStatus(payment))
				.willReturn(com.loopers.payment.payment.application.dto.out.PgRecoveryResult.notFound());

			// Act
			PaymentOutDto result = paymentCommandFacade.recoverPayment(10L);

			// Assert
			assertAll(
				() -> assertThat(result.id()).isEqualTo(10L),
				() -> assertThat(result.status()).isEqualTo(PaymentStatus.REQUESTED),
				() -> verify(paymentCommandService, never()).failPayment(anyLong(), anyString())
			);
		}


		@Test
		@DisplayName("[recoverPayment()] grace 이후에도 PG에 트랜잭션 없음 -> failPayment 호출")
		void recoverNotFoundInPg() {
			// Arrange
			Payment payment = Payment.reconstruct(10L, 100L, 1L, "PGO-test", CardType.SAMSUNG, "1234-5678-9012-3456",
				new BigDecimal("20000"), null, PaymentStatus.REQUESTED, null, LocalDateTime.now().minusSeconds(10));
			given(paymentQueryService.findById(10L)).willReturn(Optional.of(payment));

			var notFound = com.loopers.payment.payment.application.dto.out.PgRecoveryResult.notFound();
			given(paymentCommandService.recoverPaymentStatus(payment)).willReturn(notFound);

			Payment updatedPayment = createTestPayment(10L, PaymentStatus.FAILED);
			given(paymentQueryService.findById(10L))
				.willReturn(Optional.of(payment))   // 첫 호출: 원본
				.willReturn(Optional.of(updatedPayment)); // 두 번째 호출: 갱신된 상태

			// Act
			PaymentOutDto result = paymentCommandFacade.recoverPayment(10L);

			// Assert
			verify(paymentCommandService).failPayment(eq(10L), anyString());
		}


		@Test
		@DisplayName("[recoverPayment()] transactionKey 기반 PG 조회 -> updatePaymentResult 호출")
		void recoverByTransactionKey() {
			// Arrange
			Payment payment = createTestPayment(10L, PaymentStatus.REQUESTED);
			given(paymentQueryService.findById(10L)).willReturn(Optional.of(payment));

			var pgResult = new com.loopers.payment.payment.application.dto.out.PgRecoveryResult(
				true, "tx-123", "SUCCESS", "정상 승인", true);
			given(paymentCommandService.recoverPaymentStatus(payment)).willReturn(pgResult);

			Payment updatedPayment = createTestPayment(10L, PaymentStatus.SUCCESS);
			given(paymentQueryService.findById(10L))
				.willReturn(Optional.of(payment))
				.willReturn(Optional.of(updatedPayment));

			// Act
			PaymentOutDto result = paymentCommandFacade.recoverPayment(10L);

			// Assert
			verify(paymentCommandService).updatePaymentResult("tx-123", "SUCCESS", "정상 승인");
		}


		@Test
		@DisplayName("[recoverPayment()] orderId 기반 PG 조회 -> updatePaymentResultByOrderId 호출")
		void recoverByOrderId() {
			// Arrange
			Payment payment = createTestPayment(10L, PaymentStatus.REQUESTED);
			given(paymentQueryService.findById(10L)).willReturn(Optional.of(payment));

			var pgResult = new com.loopers.payment.payment.application.dto.out.PgRecoveryResult(
				true, "tx-456", "SUCCESS", "정상 승인", false);
			given(paymentCommandService.recoverPaymentStatus(payment)).willReturn(pgResult);

			Payment updatedPayment = createTestPayment(10L, PaymentStatus.SUCCESS);
			given(paymentQueryService.findById(10L))
				.willReturn(Optional.of(payment))
				.willReturn(Optional.of(updatedPayment));

			// Act
			PaymentOutDto result = paymentCommandFacade.recoverPayment(10L);

			// Assert
			verify(paymentCommandService).updatePaymentResultByOrderId(1L, "tx-456", "SUCCESS", "정상 승인");
		}


		@Test
		@DisplayName("[recoverPayment()] PG 조회 실패 -> catch. 현재 상태 그대로 반환")
		void recoverPgQueryFailed() {
			// Arrange
			Payment payment = createTestPayment(10L, PaymentStatus.REQUESTED);
			given(paymentQueryService.findById(10L)).willReturn(Optional.of(payment));
			given(paymentCommandService.recoverPaymentStatus(payment)).willThrow(new RuntimeException("PG 연결 실패"));

			// Act
			PaymentOutDto result = paymentCommandFacade.recoverPayment(10L);

			// Assert — failPayment 미호출, 현재 상태 반환
			assertAll(
				() -> assertThat(result.id()).isEqualTo(10L),
				() -> verify(paymentCommandService, never()).failPayment(anyLong(), anyString())
			);
		}


		@Test
		@DisplayName("[recoverPayment()] 존재하지 않는 paymentId -> PAYMENT_NOT_FOUND 예외")
		void recoverPaymentNotFound() {
			// Arrange
			given(paymentQueryService.findById(999L)).willReturn(Optional.empty());

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> paymentCommandFacade.recoverPayment(999L));

			// Assert
			assertThat(exception.getErrorType()).isEqualTo(ErrorType.PAYMENT_NOT_FOUND);
		}

	}


	@Nested
	@DisplayName("handleCallback() 테스트")
	class HandleCallbackTest {

		@Test
		@DisplayName("[handleCallback()] REQUESTED Payment + SUCCESS 콜백 -> updatePaymentResult 호출")
		void handleCallbackSuccess() {
			// Arrange
			Payment payment = createTestPayment(10L, PaymentStatus.REQUESTED);
			given(paymentQueryService.findByTransactionKey("tx-123")).willReturn(Optional.of(payment));

			// Act
			paymentCommandFacade.handleCallback("tx-123", "SUCCESS", "정상 승인");

			// Assert
			verify(paymentCommandService).updatePaymentResult("tx-123", "SUCCESS", "정상 승인");
		}


		@Test
		@DisplayName("[handleCallback()] 이미 SUCCESS 상태 -> skip (멱등)")
		void handleCallbackAlreadySuccess() {
			// Arrange
			Payment payment = createTestPayment(10L, PaymentStatus.SUCCESS);
			given(paymentQueryService.findByTransactionKey("tx-123")).willReturn(Optional.of(payment));

			// Act
			paymentCommandFacade.handleCallback("tx-123", "SUCCESS", "정상 승인");

			// Assert — updatePaymentResult 미호출
			verify(paymentCommandService, never()).updatePaymentResult(anyString(), anyString(), anyString());
		}


		@Test
		@DisplayName("[handleCallback()] 존재하지 않는 transactionKey -> PAYMENT_NOT_FOUND 예외")
		void handleCallbackNotFound() {
			// Arrange
			given(paymentQueryService.findByTransactionKey("nonexistent")).willReturn(Optional.empty());

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> paymentCommandFacade.handleCallback("nonexistent", "SUCCESS", "정상 승인"));

			// Assert
			assertThat(exception.getErrorType()).isEqualTo(ErrorType.PAYMENT_NOT_FOUND);
		}

	}

}
