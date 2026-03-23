package com.loopers.payment.payment.infrastructure.mapper;


import com.loopers.payment.payment.domain.model.Payment;
import com.loopers.payment.payment.domain.model.enums.CardType;
import com.loopers.payment.payment.domain.model.enums.PaymentStatus;
import com.loopers.payment.payment.infrastructure.entity.PaymentEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;


@DisplayName("PaymentEntityMapper 테스트")
class PaymentEntityMapperTest {

	private PaymentEntityMapper mapper;

	@BeforeEach
	void setUp() {
		mapper = new PaymentEntityMapper();
	}


	@Test
	@DisplayName("[toEntity()] Payment 도메인 -> PaymentEntity 변환. 모든 필드 매핑")
	void toEntity() {
		// Arrange
		Payment payment = Payment.reconstruct(1L, 2L, 100L, "PGO-test", CardType.SAMSUNG, "1234-5678-9012-3456",
			new BigDecimal("50000"), "txn-key-123", PaymentStatus.REQUESTED, null,
			LocalDateTime.of(2026, 3, 16, 10, 0));

		// Act
		PaymentEntity entity = mapper.toEntity(payment);

		// Assert
		assertAll(
			() -> assertThat(entity.getId()).isEqualTo(1L),
			() -> assertThat(entity.getUserId()).isEqualTo(2L),
			() -> assertThat(entity.getOrderId()).isEqualTo(100L),
			() -> assertThat(entity.getPgOrderId()).isEqualTo("PGO-test"),
			() -> assertThat(entity.getCardType()).isEqualTo(CardType.SAMSUNG),
			() -> assertThat(entity.getCardNo()).isEqualTo("1234-5678-9012-3456"),
			() -> assertThat(entity.getAmount()).isEqualByComparingTo(new BigDecimal("50000")),
			() -> assertThat(entity.getTransactionKey()).isEqualTo("txn-key-123"),
			() -> assertThat(entity.getStatus()).isEqualTo(PaymentStatus.REQUESTED),
			() -> assertThat(entity.getFailureReason()).isNull()
		);
	}


	@Test
	@DisplayName("[toDomain()] PaymentEntity -> Payment 도메인 변환. 비영속 Entity의 createdAt은 null 허용")
	void toDomain() {
		// Arrange
		PaymentEntity entity = PaymentEntity.of(1L, 2L, 100L, "PGO-test", CardType.KB, "9999-8888-7777-6666",
			new BigDecimal("30000"), "txn-key-456", PaymentStatus.SUCCESS, null);

		// Act — createdAt은 @PrePersist에서 설정되므로 테스트에서는 null 허용
		Payment payment = mapper.toDomain(entity);

		// Assert
		assertAll(
			() -> assertThat(payment.getId()).isEqualTo(1L),
			() -> assertThat(payment.getUserId()).isEqualTo(2L),
			() -> assertThat(payment.getOrderId()).isEqualTo(100L),
			() -> assertThat(payment.getPgOrderId()).isEqualTo("PGO-test"),
			() -> assertThat(payment.getCardType()).isEqualTo(CardType.KB),
			() -> assertThat(payment.getCardNo()).isEqualTo("9999-8888-7777-6666"),
			() -> assertThat(payment.getAmount()).isEqualByComparingTo(new BigDecimal("30000")),
			() -> assertThat(payment.getTransactionKey()).isEqualTo("txn-key-456"),
			() -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS),
			() -> assertThat(payment.getFailureReason()).isNull(),
			() -> assertThat(payment.getCreatedAt()).isNull()
		);
	}

}
