package com.loopers.payment.payment.application.service;


import com.loopers.payment.payment.domain.model.Payment;
import com.loopers.payment.payment.domain.model.enums.CardType;
import com.loopers.payment.payment.domain.model.enums.PaymentStatus;
import com.loopers.payment.payment.domain.repository.PaymentQueryRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;


@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentQueryService 테스트")
class PaymentQueryServiceTest {

	@Mock
	private PaymentQueryRepository paymentQueryRepository;

	private PaymentQueryService paymentQueryService;

	@BeforeEach
	void setUp() {
		paymentQueryService = new PaymentQueryService(paymentQueryRepository);
	}


	private Payment createTestPayment(Long id, Long orderId, PaymentStatus status) {
		return Payment.reconstruct(id, 100L, orderId, "PGO-test", CardType.SAMSUNG, "1234-5678-9012-3456",
			new BigDecimal("200000"), "tx-" + id, status, null, LocalDateTime.now());
	}


	@Nested
	@DisplayName("findByOrderIdAndStatus() 테스트")
	class FindByOrderIdAndStatusTest {

		@Test
		@DisplayName("[findByOrderIdAndStatus()] 존재하는 주문+상태 -> Optional<Payment> 반환")
		void findSuccess() {
			// Arrange
			Payment payment = createTestPayment(1L, 10L, PaymentStatus.REQUESTED);
			given(paymentQueryRepository.findByOrderIdAndStatus(10L, PaymentStatus.REQUESTED))
				.willReturn(Optional.of(payment));

			// Act
			Optional<Payment> result = paymentQueryService.findByOrderIdAndStatus(10L, PaymentStatus.REQUESTED);

			// Assert
			assertAll(
				() -> assertThat(result).isPresent(),
				() -> assertThat(result.get().getId()).isEqualTo(1L)
			);
		}

	}


	@Nested
	@DisplayName("findRequestedPaymentsCreatedBefore() 테스트")
	class FindRequestedPaymentsTest {

		@Test
		@DisplayName("[findRequestedPaymentsCreatedBefore()] REQUESTED + 5초 이상 -> 대상 목록 반환")
		void findPendingPayments() {
			// Arrange
			Payment payment = createTestPayment(1L, 10L, PaymentStatus.REQUESTED);
			given(paymentQueryRepository.findRequestedPaymentsCreatedBefore(any(LocalDateTime.class)))
				.willReturn(List.of(payment));

			// Act
			List<Payment> result = paymentQueryService.findRequestedPaymentsCreatedBefore(5);

			// Assert
			assertThat(result).hasSize(1);
		}

	}


	@Nested
	@DisplayName("existsRequestedByOrderId() 테스트")
	class ExistsRequestedTest {

		@Test
		@DisplayName("[existsRequestedByOrderId()] REQUESTED 결제 존재 -> true")
		void existsTrue() {
			// Arrange
			given(paymentQueryRepository.existsRequestedByOrderId(10L)).willReturn(true);

			// Act & Assert
			assertThat(paymentQueryService.existsRequestedByOrderId(10L)).isTrue();
		}

		@Test
		@DisplayName("[existsRequestedByOrderId()] REQUESTED 결제 없음 -> false")
		void existsFalse() {
			// Arrange
			given(paymentQueryRepository.existsRequestedByOrderId(10L)).willReturn(false);

			// Act & Assert
			assertThat(paymentQueryService.existsRequestedByOrderId(10L)).isFalse();
		}

	}

}
