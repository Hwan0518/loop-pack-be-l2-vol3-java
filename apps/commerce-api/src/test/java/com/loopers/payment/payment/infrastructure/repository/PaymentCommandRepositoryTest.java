package com.loopers.payment.payment.infrastructure.repository;


import com.loopers.payment.payment.domain.model.Payment;
import com.loopers.payment.payment.domain.model.enums.CardType;
import com.loopers.payment.payment.domain.model.enums.PaymentStatus;
import com.loopers.payment.payment.domain.repository.PaymentCommandRepository;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;


@SpringBootTest
@ActiveProfiles("test")
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
@DisplayName("PaymentCommandRepository 통합 테스트")
class PaymentCommandRepositoryTest {

	@Autowired
	private PaymentCommandRepository paymentCommandRepository;

	@Autowired
	private DatabaseCleanUp databaseCleanUp;


	@AfterEach
	void tearDown() {
		databaseCleanUp.truncateAllTables();
	}


	@Test
	@DisplayName("[PaymentCommandRepository.save()] 유효한 결제 저장 -> ID가 할당된 Payment 반환")
	void savePayment() {
		// Arrange
		Payment payment = Payment.create(1L, 100L, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000"));

		// Act
		Payment savedPayment = paymentCommandRepository.save(payment);

		// Assert
		assertAll(
			() -> assertThat(savedPayment.getId()).isNotNull(),
			() -> assertThat(savedPayment.getUserId()).isEqualTo(1L),
			() -> assertThat(savedPayment.getOrderId()).isEqualTo(100L),
			() -> assertThat(savedPayment.getPgOrderId()).startsWith("PGO-100-"),
			() -> assertThat(savedPayment.getCardType()).isEqualTo(CardType.SAMSUNG),
			() -> assertThat(savedPayment.getCardNo()).isEqualTo("1234-5678-9012-3456"),
			() -> assertThat(savedPayment.getAmount()).isEqualByComparingTo(new BigDecimal("50000")),
			() -> assertThat(savedPayment.getStatus()).isEqualTo(PaymentStatus.REQUESTED),
			() -> assertThat(savedPayment.getCreatedAt()).isNotNull()
		);
	}


	@Test
	@DisplayName("[PaymentCommandRepository.save()] 결제 상태 변경 후 재저장 -> 변경된 상태 반영")
	void savePaymentAfterStatusChange() {
		// Arrange
		Payment payment = Payment.create(1L, 100L, CardType.KB, "9999-8888-7777-6666", new BigDecimal("30000"));
		Payment savedPayment = paymentCommandRepository.save(payment);

		// 상태 변경
		savedPayment.updateTransactionKey("txn-key-123");
		savedPayment.succeed();

		// Act
		Payment updatedPayment = paymentCommandRepository.save(savedPayment);

		// Assert
		assertAll(
			() -> assertThat(updatedPayment.getId()).isEqualTo(savedPayment.getId()),
			() -> assertThat(updatedPayment.getTransactionKey()).isEqualTo("txn-key-123"),
			() -> assertThat(updatedPayment.getStatus()).isEqualTo(PaymentStatus.SUCCESS)
		);
	}


	@Test
	@DisplayName("[PaymentCommandRepository.save()] 동일한 pgOrderId 저장 시도 -> unique 제약 위반")
	void saveDuplicatePgOrderId() {
		// Arrange
		String duplicatedPgOrderId = "PGO-100-duplicate-check-value";
		Payment firstPayment = Payment.reconstruct(
			null, 1L, 100L, duplicatedPgOrderId, CardType.SAMSUNG, "1234-5678-9012-3456",
			new BigDecimal("50000"), null, PaymentStatus.REQUESTED, null, null
		);
		Payment secondPayment = Payment.reconstruct(
			null, 2L, 200L, duplicatedPgOrderId, CardType.KB, "9999-8888-7777-6666",
			new BigDecimal("30000"), null, PaymentStatus.REQUESTED, null, null
		);
		paymentCommandRepository.save(firstPayment);

		// Act & Assert
		assertThrows(DataIntegrityViolationException.class,
			() -> paymentCommandRepository.save(secondPayment));
	}

}
