package com.loopers.payment.payment.infrastructure.repository;


import com.loopers.payment.payment.domain.model.Payment;
import com.loopers.payment.payment.domain.model.enums.CardType;
import com.loopers.payment.payment.domain.model.enums.PaymentStatus;
import com.loopers.payment.payment.domain.repository.PaymentCommandRepository;
import com.loopers.payment.payment.domain.repository.PaymentQueryRepository;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;


@SpringBootTest
@ActiveProfiles("test")
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
@DisplayName("PaymentQueryRepository 통합 테스트")
class PaymentQueryRepositoryTest {

	@Autowired
	private PaymentQueryRepository paymentQueryRepository;

	@Autowired
	private PaymentCommandRepository paymentCommandRepository;

	@Autowired
	private DatabaseCleanUp databaseCleanUp;


	@AfterEach
	void tearDown() {
		databaseCleanUp.truncateAllTables();
	}


	@Nested
	@DisplayName("findById 테스트")
	class FindByIdTest {

		@Test
		@DisplayName("[findById()] 존재하는 ID -> Payment 반환")
		void findById() {
			// Arrange
			Payment saved = paymentCommandRepository.save(
				Payment.create(1L, 100L, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000")));

			// Act
			Optional<Payment> result = paymentQueryRepository.findById(saved.getId());

			// Assert
			assertAll(
				() -> assertThat(result).isPresent(),
				() -> assertThat(result.get().getId()).isEqualTo(saved.getId()),
				() -> assertThat(result.get().getUserId()).isEqualTo(1L)
			);
		}

		@Test
		@DisplayName("[findById()] 존재하지 않는 ID -> empty Optional 반환")
		void findByIdNotFound() {
			// Act
			Optional<Payment> result = paymentQueryRepository.findById(999L);

			// Assert
			assertThat(result).isEmpty();
		}
	}


	@Nested
	@DisplayName("findByOrderIdAndStatus 테스트")
	class FindByOrderIdAndStatusTest {

		@Test
		@DisplayName("[findByOrderIdAndStatus()] 주문 ID + 상태 일치 -> Payment 반환")
		void findByOrderIdAndStatus() {
			// Arrange
			paymentCommandRepository.save(
				Payment.create(1L, 100L, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000")));

			// Act
			Optional<Payment> result = paymentQueryRepository.findByOrderIdAndStatus(100L, PaymentStatus.REQUESTED);

			// Assert
			assertAll(
				() -> assertThat(result).isPresent(),
				() -> assertThat(result.get().getOrderId()).isEqualTo(100L),
				() -> assertThat(result.get().getStatus()).isEqualTo(PaymentStatus.REQUESTED)
			);
		}

		@Test
		@DisplayName("[findByOrderIdAndStatus()] 주문 ID 불일치 -> empty Optional 반환")
		void findByOrderIdAndStatusNotFound() {
			// Arrange
			paymentCommandRepository.save(
				Payment.create(1L, 100L, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000")));

			// Act
			Optional<Payment> result = paymentQueryRepository.findByOrderIdAndStatus(999L, PaymentStatus.REQUESTED);

			// Assert
			assertThat(result).isEmpty();
		}
	}


	@Nested
	@DisplayName("findByTransactionKey 테스트")
	class FindByTransactionKeyTest {

		@Test
		@DisplayName("[findByTransactionKey()] 트랜잭션 키 일치 -> Payment 반환")
		void findByTransactionKey() {
			// Arrange
			Payment payment = Payment.create(1L, 100L, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000"));
			payment.updateTransactionKey("txn-key-123");
			paymentCommandRepository.save(payment);

			// Act
			Optional<Payment> result = paymentQueryRepository.findByTransactionKey("txn-key-123");

			// Assert
			assertAll(
				() -> assertThat(result).isPresent(),
				() -> assertThat(result.get().getTransactionKey()).isEqualTo("txn-key-123")
			);
		}

		@Test
		@DisplayName("[findByTransactionKey()] 트랜잭션 키 불일치 -> empty Optional 반환")
		void findByTransactionKeyNotFound() {
			// Act
			Optional<Payment> result = paymentQueryRepository.findByTransactionKey("non-existent-key");

			// Assert
			assertThat(result).isEmpty();
		}
	}


	@Nested
	@DisplayName("findRequestedByOrderId 테스트")
	class FindRequestedByOrderIdTest {

		@Test
		@DisplayName("[findRequestedByOrderId()] REQUESTED 상태 결제 존재 -> Payment 반환")
		void findRequestedByOrderId() {
			// Arrange
			paymentCommandRepository.save(
				Payment.create(1L, 100L, CardType.KB, "1111-2222-3333-4444", new BigDecimal("30000")));

			// Act
			Optional<Payment> result = paymentQueryRepository.findRequestedByOrderId(100L);

			// Assert
			assertAll(
				() -> assertThat(result).isPresent(),
				() -> assertThat(result.get().getOrderId()).isEqualTo(100L),
				() -> assertThat(result.get().getStatus()).isEqualTo(PaymentStatus.REQUESTED)
			);
		}

		@Test
		@DisplayName("[findRequestedByOrderId()] SUCCESS 상태만 존재 -> empty Optional 반환")
		void findRequestedByOrderIdWhenOnlySuccess() {
			// Arrange
			Payment payment = Payment.create(1L, 100L, CardType.KB, "1111-2222-3333-4444", new BigDecimal("30000"));
			payment.updateTransactionKey("txn-key");
			payment.succeed();
			paymentCommandRepository.save(payment);

			// Act
			Optional<Payment> result = paymentQueryRepository.findRequestedByOrderId(100L);

			// Assert
			assertThat(result).isEmpty();
		}
	}


	@Nested
	@DisplayName("existsRequestedByOrderId 테스트")
	class ExistsRequestedByOrderIdTest {

		@Test
		@DisplayName("[existsRequestedByOrderId()] REQUESTED 상태 결제 존재 -> true 반환")
		void existsRequestedByOrderId() {
			// Arrange
			paymentCommandRepository.save(
				Payment.create(1L, 100L, CardType.HYUNDAI, "5555-6666-7777-8888", new BigDecimal("20000")));

			// Act
			boolean result = paymentQueryRepository.existsRequestedByOrderId(100L);

			// Assert
			assertThat(result).isTrue();
		}

		@Test
		@DisplayName("[existsRequestedByOrderId()] REQUESTED 상태 결제 미존재 -> false 반환")
		void existsRequestedByOrderIdNotExists() {
			// Act
			boolean result = paymentQueryRepository.existsRequestedByOrderId(999L);

			// Assert
			assertThat(result).isFalse();
		}
	}


	@Nested
	@DisplayName("findRequestedPaymentsCreatedBefore 테스트")
	class FindRequestedPaymentsCreatedBeforeTest {

		@Test
		@DisplayName("[findRequestedPaymentsCreatedBefore()] 기준 시간 이전 REQUESTED 결제 존재 -> 해당 결제 목록 반환")
		void findRequestedPaymentsCreatedBefore() {
			// Arrange
			paymentCommandRepository.save(
				Payment.create(1L, 100L, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000")));
			paymentCommandRepository.save(
				Payment.create(2L, 200L, CardType.KB, "9999-8888-7777-6666", new BigDecimal("30000")));

			// Act — 미래 시간으로 조회하면 모든 REQUESTED 결제가 조회됨
			List<Payment> result = paymentQueryRepository.findRequestedPaymentsCreatedBefore(
				LocalDateTime.now().plusHours(1));

			// Assert
			assertThat(result).hasSize(2);
		}

		@Test
		@DisplayName("[findRequestedPaymentsCreatedBefore()] SUCCESS 상태 결제는 제외됨")
		void findRequestedPaymentsExcludesNonRequested() {
			// Arrange
			Payment requestedPayment = Payment.create(1L, 100L, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000"));
			paymentCommandRepository.save(requestedPayment);

			Payment successPayment = Payment.create(2L, 200L, CardType.KB, "9999-8888-7777-6666", new BigDecimal("30000"));
			successPayment.updateTransactionKey("txn-key");
			successPayment.succeed();
			paymentCommandRepository.save(successPayment);

			// Act
			List<Payment> result = paymentQueryRepository.findRequestedPaymentsCreatedBefore(
				LocalDateTime.now().plusHours(1));

			// Assert
			assertAll(
				() -> assertThat(result).hasSize(1),
				() -> assertThat(result.get(0).getOrderId()).isEqualTo(100L)
			);
		}
	}

}
