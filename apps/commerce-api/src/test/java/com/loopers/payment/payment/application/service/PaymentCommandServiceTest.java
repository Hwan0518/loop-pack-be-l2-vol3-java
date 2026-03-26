package com.loopers.payment.payment.application.service;


import com.loopers.payment.payment.application.port.out.client.order.PaymentOrderInfo;
import com.loopers.payment.payment.application.port.out.client.order.PaymentOrderItemInfo;
import com.loopers.payment.payment.application.port.out.client.order.PaymentOrderReader;
import com.loopers.payment.payment.application.port.out.client.order.PaymentOrderStatusManager;
import com.loopers.payment.payment.application.port.out.client.pg.PgPaymentGateway;
import com.loopers.payment.payment.domain.event.OrderPaidEvent;
import com.loopers.payment.payment.domain.model.Payment;
import com.loopers.payment.payment.domain.model.enums.CardType;
import com.loopers.payment.payment.domain.model.enums.PaymentStatus;
import com.loopers.payment.payment.domain.repository.PaymentCommandRepository;
import com.loopers.payment.payment.domain.repository.PaymentQueryRepository;
import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import com.loopers.support.common.outbox.application.port.OutboxEventPort;
import com.loopers.support.common.outbox.application.util.JsonSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentCommandService 테스트")
class PaymentCommandServiceTest {

	@Mock
	private PaymentCommandRepository paymentCommandRepository;
	@Mock
	private PaymentQueryRepository paymentQueryRepository;
	@Mock
	private PaymentOrderReader paymentOrderReader;
	@Mock
	private PaymentOrderStatusManager paymentOrderStatusManager;
	@Mock
	private PgPaymentGateway pgPaymentGateway;
	@Mock
	private OutboxEventPort outboxEventPort;
	@Mock
	private JsonSerializer jsonSerializer;
	@Mock
	private ApplicationEventPublisher eventPublisher;

	private PaymentCommandService paymentCommandService;

	@BeforeEach
	void setUp() {
		paymentCommandService = new PaymentCommandService(
			paymentCommandRepository, paymentQueryRepository,
			paymentOrderReader, paymentOrderStatusManager,
			pgPaymentGateway, outboxEventPort, jsonSerializer,
			eventPublisher, "http://localhost:8080/callback"
		);
	}


	@Nested
	@DisplayName("createPaymentWithLock() 테스트")
	class CreatePaymentWithLockTest {

		@Test
		@DisplayName("[createPaymentWithLock()] 유효한 최초 결제 요청 -> Payment 생성 (REQUESTED). Order 비관적 락 + 멱등성 체크")
		void createPaymentSuccess() {
			// Arrange
			PaymentOrderInfo orderInfo = new PaymentOrderInfo(1L, 100L, new BigDecimal("200000"), "PENDING_PAYMENT");
			given(paymentOrderReader.findOrderForPayment(1L, 100L)).willReturn(orderInfo);
			given(paymentQueryRepository.findByOrderIdAndStatus(1L, PaymentStatus.REQUESTED)).willReturn(Optional.empty());
			given(paymentCommandRepository.save(any(Payment.class))).willAnswer(invocation -> {
				Payment p = invocation.getArgument(0);
				return Payment.reconstruct(10L, p.getUserId(), p.getOrderId(), p.getPgOrderId(), p.getCardType(),
					p.getCardNo(), p.getAmount(), null, p.getStatus(), null, LocalDateTime.now());
			});

			// Act
			Payment result = paymentCommandService.createPaymentWithLock(100L, 1L, "SAMSUNG", "1234-5678-9012-3456");

			// Assert
			assertAll(
				() -> assertThat(result.getId()).isEqualTo(10L),
				() -> assertThat(result.getStatus()).isEqualTo(PaymentStatus.REQUESTED),
				() -> assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("200000")),
				() -> verify(paymentOrderReader).findOrderForPayment(1L, 100L)
			);
		}


		@Test
		@DisplayName("[createPaymentWithLock()] PAID 상태 주문 -> ORDER_NOT_PAYABLE 예외")
		void createPaymentOrderNotPayable() {
			// Arrange
			PaymentOrderInfo orderInfo = new PaymentOrderInfo(1L, 100L, new BigDecimal("200000"), "PAID");
			given(paymentOrderReader.findOrderForPayment(1L, 100L)).willReturn(orderInfo);

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> paymentCommandService.createPaymentWithLock(100L, 1L, "SAMSUNG", "1234-5678-9012-3456"));

			// Assert
			assertThat(exception.getErrorType()).isEqualTo(ErrorType.ORDER_NOT_PAYABLE);
		}


		@Test
		@DisplayName("[createPaymentWithLock()] REQUESTED 결제 존재 -> PAYMENT_ALREADY_IN_PROGRESS 예외")
		void createPaymentAlreadyInProgress() {
			// Arrange
			PaymentOrderInfo orderInfo = new PaymentOrderInfo(1L, 100L, new BigDecimal("200000"), "PENDING_PAYMENT");
			given(paymentOrderReader.findOrderForPayment(1L, 100L)).willReturn(orderInfo);
			Payment existing = Payment.reconstruct(5L, 100L, 1L, "PGO-test", CardType.SAMSUNG, "1234-5678-9012-3456",
				new BigDecimal("200000"), null, PaymentStatus.REQUESTED, null, LocalDateTime.now());
			given(paymentQueryRepository.findByOrderIdAndStatus(1L, PaymentStatus.REQUESTED)).willReturn(Optional.of(existing));

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> paymentCommandService.createPaymentWithLock(100L, 1L, "SAMSUNG", "1234-5678-9012-3456"));

			// Assert
			assertThat(exception.getErrorType()).isEqualTo(ErrorType.PAYMENT_ALREADY_IN_PROGRESS);
		}


		@Test
		@DisplayName("[createPaymentWithLock()] PAYMENT_FAILED 주문 재결제 -> Payment 생성 + Order PENDING_PAYMENT로 변경")
		void createPaymentRetry() {
			// Arrange
			PaymentOrderInfo orderInfo = new PaymentOrderInfo(1L, 100L, new BigDecimal("200000"), "PAYMENT_FAILED");
			given(paymentOrderReader.findOrderForPayment(1L, 100L)).willReturn(orderInfo);
			given(paymentQueryRepository.findByOrderIdAndStatus(1L, PaymentStatus.REQUESTED)).willReturn(Optional.empty());
			given(paymentCommandRepository.save(any(Payment.class))).willAnswer(invocation -> {
				Payment p = invocation.getArgument(0);
				return Payment.reconstruct(10L, p.getUserId(), p.getOrderId(), p.getPgOrderId(), p.getCardType(),
					p.getCardNo(), p.getAmount(), null, p.getStatus(), null, LocalDateTime.now());
			});

			// Act
			Payment result = paymentCommandService.createPaymentWithLock(100L, 1L, "SAMSUNG", "1234-5678-9012-3456");

			// Assert
			assertAll(
				() -> assertThat(result.getStatus()).isEqualTo(PaymentStatus.REQUESTED),
				() -> verify(paymentOrderStatusManager).markOrderPendingPayment(1L)
			);
		}

	}


	@Nested
	@DisplayName("failPayment() 테스트")
	class FailPaymentTest {

		@Test
		@DisplayName("[failPayment()] REQUESTED 상태 Payment -> FAILED + Order PAYMENT_FAILED. 실패 사유 저장")
		void failPaymentSuccess() {
			// Arrange
			Payment payment = Payment.reconstruct(10L, 100L, 1L, "PGO-test", CardType.SAMSUNG, "1234-5678-9012-3456",
				new BigDecimal("200000"), "tx-123", PaymentStatus.REQUESTED, null, LocalDateTime.now());
			given(paymentQueryRepository.findById(10L)).willReturn(Optional.of(payment));
			given(paymentCommandRepository.save(any(Payment.class))).willAnswer(invocation -> invocation.getArgument(0));

			// Act
			paymentCommandService.failPayment(10L, "PG 서버 오류");

			// Assert
			assertAll(
				() -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED),
				() -> assertThat(payment.getFailureReason()).isEqualTo("PG 서버 오류"),
				() -> verify(paymentOrderStatusManager).markOrderPaymentFailed(1L)
			);
		}


		@Test
		@DisplayName("[failPayment()] 이미 FAILED 상태 Payment -> skip (멱등)")
		void failPaymentAlreadyFailed() {
			// Arrange
			Payment payment = Payment.reconstruct(10L, 100L, 1L, "PGO-test", CardType.SAMSUNG, "1234-5678-9012-3456",
				new BigDecimal("200000"), "tx-123", PaymentStatus.FAILED, "이전 실패", LocalDateTime.now());
			given(paymentQueryRepository.findById(10L)).willReturn(Optional.of(payment));

			// Act
			paymentCommandService.failPayment(10L, "새 실패 사유");

			// Assert — skip, 상태 변경 없음
			assertAll(
				() -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED),
				() -> assertThat(payment.getFailureReason()).isEqualTo("이전 실패")
			);
		}

	}


	@Nested
	@DisplayName("updatePaymentResult() 테스트")
	class UpdatePaymentResultTest {

		@Test
		@DisplayName("[updatePaymentResult()] PG SUCCESS -> Payment SUCCESS + Order PAID + OrderPaidEvent 발행")
		void updatePaymentResultSuccess() {
			// Arrange
			Payment payment = Payment.reconstruct(10L, 100L, 1L, "PGO-test", CardType.SAMSUNG, "1234-5678-9012-3456",
				new BigDecimal("200000"), "tx-123", PaymentStatus.REQUESTED, null, LocalDateTime.now());
			given(paymentQueryRepository.findByTransactionKey("tx-123")).willReturn(Optional.of(payment));
			given(paymentCommandRepository.save(any(Payment.class))).willAnswer(invocation -> invocation.getArgument(0));
			given(paymentOrderReader.findOrderItems(1L)).willReturn(
				List.of(new PaymentOrderItemInfo(1L, 2L)));

			// Act
			paymentCommandService.updatePaymentResult("tx-123", "SUCCESS", null);

			// Assert
			ArgumentCaptor<OrderPaidEvent> eventCaptor = ArgumentCaptor.forClass(OrderPaidEvent.class);
			assertAll(
				() -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS),
				() -> verify(paymentOrderStatusManager).markOrderPaid(1L),
				() -> verify(paymentOrderReader).findOrderItems(1L),
				() -> verify(eventPublisher).publishEvent(eventCaptor.capture())
			);

			OrderPaidEvent event = eventCaptor.getValue();
			assertAll(
				() -> assertThat(event.orderId()).isEqualTo(1L),
				() -> assertThat(event.userId()).isEqualTo(100L),
				() -> assertThat(event.paymentId()).isEqualTo(10L),
				() -> assertThat(event.items()).hasSize(1),
				() -> assertThat(event.items().get(0).productId()).isEqualTo(1L),
				() -> assertThat(event.items().get(0).quantity()).isEqualTo(2L),
				() -> assertThat(event.totalPrice()).isEqualByComparingTo(new BigDecimal("200000"))
			);
		}


		@Test
		@DisplayName("[updatePaymentResult()] PG FAILED -> Payment FAILED + Order PAYMENT_FAILED")
		void updatePaymentResultFailed() {
			// Arrange
			Payment payment = Payment.reconstruct(10L, 100L, 1L, "PGO-test", CardType.SAMSUNG, "1234-5678-9012-3456",
				new BigDecimal("200000"), "tx-123", PaymentStatus.REQUESTED, null, LocalDateTime.now());
			given(paymentQueryRepository.findByTransactionKey("tx-123")).willReturn(Optional.of(payment));
			given(paymentCommandRepository.save(any(Payment.class))).willAnswer(invocation -> invocation.getArgument(0));

			// Act
			paymentCommandService.updatePaymentResult("tx-123", "FAILED", "잔액 부족");

			// Assert
			assertAll(
				() -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED),
				() -> assertThat(payment.getFailureReason()).isEqualTo("잔액 부족"),
				() -> verify(paymentOrderStatusManager).markOrderPaymentFailed(1L)
			);
		}


		@Test
		@DisplayName("[updatePaymentResult()] 이미 SUCCESS 상태 -> skip (멱등)")
		void updatePaymentResultAlreadySuccess() {
			// Arrange
			Payment payment = Payment.reconstruct(10L, 100L, 1L, "PGO-test", CardType.SAMSUNG, "1234-5678-9012-3456",
				new BigDecimal("200000"), "tx-123", PaymentStatus.SUCCESS, null, LocalDateTime.now());
			given(paymentQueryRepository.findByTransactionKey("tx-123")).willReturn(Optional.of(payment));

			// Act
			paymentCommandService.updatePaymentResult("tx-123", "SUCCESS", null);

			// Assert — skip, 상태 변경 없음
			assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
		}

	}

}
