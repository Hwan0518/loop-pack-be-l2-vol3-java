package com.loopers.payment.payment.domain.model;


import com.loopers.payment.payment.domain.model.enums.CardType;
import com.loopers.payment.payment.domain.model.enums.PaymentStatus;
import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;


@DisplayName("Payment 도메인 모델 테스트")
class PaymentTest {

	@Nested
	@DisplayName("create() 테스트")
	class CreateTest {

		@Test
		@DisplayName("[create()] 유효한 결제 정보 -> Payment 생성. status=REQUESTED, id/transactionKey/failureReason/createdAt=null")
		void createPayment() {
			// Act
			Payment payment = Payment.create(1L, 100L, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000"));

			// Assert
			assertAll(
				() -> assertThat(payment.getId()).isNull(),
				() -> assertThat(payment.getUserId()).isEqualTo(1L),
				() -> assertThat(payment.getOrderId()).isEqualTo(100L),
				() -> assertThat(payment.getPgOrderId()).startsWith("PGO-100-"),
				() -> assertThat(payment.getPgOrderId()).hasSize(40),
				() -> assertThat(payment.getCardType()).isEqualTo(CardType.SAMSUNG),
				() -> assertThat(payment.getCardNo()).isEqualTo("1234-5678-9012-3456"),
				() -> assertThat(payment.getAmount()).isEqualByComparingTo(new BigDecimal("50000")),
				() -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REQUESTED),
				() -> assertThat(payment.getTransactionKey()).isNull(),
				() -> assertThat(payment.getFailureReason()).isNull(),
				() -> assertThat(payment.getCreatedAt()).isNull()
			);
		}

		@Test
		@DisplayName("[create()] userId가 null -> NullPointerException")
		void createWithNullUserId() {
			// Act & Assert
			assertThrows(NullPointerException.class,
				() -> Payment.create(null, 100L, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000")));
		}

		@Test
		@DisplayName("[create()] orderId가 null -> NullPointerException")
		void createWithNullOrderId() {
			// Act & Assert
			assertThrows(NullPointerException.class,
				() -> Payment.create(1L, null, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000")));
		}

		@Test
		@DisplayName("[create()] cardType이 null -> NullPointerException")
		void createWithNullCardType() {
			// Act & Assert
			assertThrows(NullPointerException.class,
				() -> Payment.create(1L, 100L, null, "1234-5678-9012-3456", new BigDecimal("50000")));
		}

		@Test
		@DisplayName("[create()] amount가 null -> NullPointerException")
		void createWithNullAmount() {
			// Act & Assert
			assertThrows(NullPointerException.class,
				() -> Payment.create(1L, 100L, CardType.SAMSUNG, "1234-5678-9012-3456", null));
		}

		@Test
		@DisplayName("[create()] amount가 0 -> BAD_REQUEST 예외. 결제 금액은 0보다 커야 함")
		void createWithZeroAmount() {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> Payment.create(1L, 100L, CardType.SAMSUNG, "1234-5678-9012-3456", BigDecimal.ZERO));

			// Assert
			assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
		}

		@Test
		@DisplayName("[create()] amount가 음수 -> BAD_REQUEST 예외. 결제 금액은 0보다 커야 함")
		void createWithNegativeAmount() {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> Payment.create(1L, 100L, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("-1000")));

			// Assert
			assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
		}

		@Test
		@DisplayName("[create()] cardNo가 null -> INVALID_CARD_NO 예외")
		void createWithNullCardNo() {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> Payment.create(1L, 100L, CardType.SAMSUNG, null, new BigDecimal("50000")));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_CARD_NO),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_CARD_NO.getMessage())
			);
		}

		@Test
		@DisplayName("[create()] cardNo 형식 불일치 (구분자 없음) -> INVALID_CARD_NO 예외")
		void createWithInvalidCardNoFormat() {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> Payment.create(1L, 100L, CardType.SAMSUNG, "1234567890123456", new BigDecimal("50000")));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_CARD_NO),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_CARD_NO.getMessage())
			);
		}

		@Test
		@DisplayName("[create()] cardNo 형식 불일치 (자릿수 부족) -> INVALID_CARD_NO 예외")
		void createWithShortCardNo() {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> Payment.create(1L, 100L, CardType.SAMSUNG, "1234-5678-9012", new BigDecimal("50000")));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_CARD_NO),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_CARD_NO.getMessage())
			);
		}
	}


	@Nested
	@DisplayName("reconstruct() 테스트")
	class ReconstructTest {

		@Test
		@DisplayName("[reconstruct()] 전체 필드 -> 검증 없이 Payment 복원")
		void reconstructPayment() {
			// Arrange
			LocalDateTime createdAt = LocalDateTime.of(2026, 3, 16, 10, 0);

			// Act
			Payment payment = Payment.reconstruct(1L, 2L, 100L, "PGO-test", CardType.KB, "1234-5678-9012-3456",
				new BigDecimal("30000"), "txn-key-123", PaymentStatus.SUCCESS, null, createdAt);

			// Assert
			assertAll(
				() -> assertThat(payment.getId()).isEqualTo(1L),
				() -> assertThat(payment.getUserId()).isEqualTo(2L),
				() -> assertThat(payment.getOrderId()).isEqualTo(100L),
				() -> assertThat(payment.getPgOrderId()).isEqualTo("PGO-test"),
				() -> assertThat(payment.getCardType()).isEqualTo(CardType.KB),
				() -> assertThat(payment.getCardNo()).isEqualTo("1234-5678-9012-3456"),
				() -> assertThat(payment.getAmount()).isEqualByComparingTo(new BigDecimal("30000")),
				() -> assertThat(payment.getTransactionKey()).isEqualTo("txn-key-123"),
				() -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS),
				() -> assertThat(payment.getFailureReason()).isNull(),
				() -> assertThat(payment.getCreatedAt()).isEqualTo(createdAt)
			);
		}
	}


	@Nested
	@DisplayName("updateTransactionKey() 테스트")
	class UpdateTransactionKeyTest {

		@Test
		@DisplayName("[updateTransactionKey()] 유효한 키 -> transactionKey 설정")
		void updateTransactionKey() {
			// Arrange
			Payment payment = Payment.create(1L, 100L, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000"));

			// Act
			payment.updateTransactionKey("txn-key-456");

			// Assert
			assertThat(payment.getTransactionKey()).isEqualTo("txn-key-456");
		}

		@Test
		@DisplayName("[updateTransactionKey()] null 키 -> BAD_REQUEST 예외")
		void updateTransactionKeyWithNull() {
			// Arrange
			Payment payment = Payment.create(1L, 100L, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000"));

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> payment.updateTransactionKey(null));

			// Assert
			assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
		}

		@Test
		@DisplayName("[updateTransactionKey()] 공백 키 -> BAD_REQUEST 예외")
		void updateTransactionKeyWithBlank() {
			// Arrange
			Payment payment = Payment.create(1L, 100L, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000"));

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> payment.updateTransactionKey("   "));

			// Assert
			assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
		}
	}


	@Nested
	@DisplayName("succeed() 테스트")
	class SucceedTest {

		@Test
		@DisplayName("[succeed()] REQUESTED 상태 -> SUCCESS로 전이")
		void succeedFromRequested() {
			// Arrange
			Payment payment = Payment.create(1L, 100L, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000"));

			// Act
			payment.succeed();

			// Assert
			assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
		}

		@Test
		@DisplayName("[succeed()] SUCCESS 상태 -> BAD_REQUEST 예외. 최종 상태에서 전이 불가")
		void succeedFromSuccess() {
			// Arrange
			Payment payment = Payment.reconstruct(1L, 2L, 100L, "PGO-test", CardType.SAMSUNG, "1234-5678-9012-3456",
				new BigDecimal("50000"), "txn-key", PaymentStatus.SUCCESS, null, LocalDateTime.now());

			// Act
			CoreException exception = assertThrows(CoreException.class, payment::succeed);

			// Assert
			assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
		}

		@Test
		@DisplayName("[succeed()] FAILED 상태 -> BAD_REQUEST 예외. 최종 상태에서 전이 불가")
		void succeedFromFailed() {
			// Arrange
			Payment payment = Payment.reconstruct(1L, 2L, 100L, "PGO-test", CardType.SAMSUNG, "1234-5678-9012-3456",
				new BigDecimal("50000"), "txn-key", PaymentStatus.FAILED, "reason", LocalDateTime.now());

			// Act
			CoreException exception = assertThrows(CoreException.class, payment::succeed);

			// Assert
			assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
		}
	}


	@Nested
	@DisplayName("fail() 테스트")
	class FailTest {

		@Test
		@DisplayName("[fail()] REQUESTED 상태 -> FAILED로 전이. failureReason 설정")
		void failFromRequested() {
			// Arrange
			Payment payment = Payment.create(1L, 100L, CardType.SAMSUNG, "1234-5678-9012-3456", new BigDecimal("50000"));

			// Act
			payment.fail("잔액 부족");

			// Assert
			assertAll(
				() -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED),
				() -> assertThat(payment.getFailureReason()).isEqualTo("잔액 부족")
			);
		}

		@Test
		@DisplayName("[fail()] SUCCESS 상태 -> BAD_REQUEST 예외. 최종 상태에서 전이 불가")
		void failFromSuccess() {
			// Arrange
			Payment payment = Payment.reconstruct(1L, 2L, 100L, "PGO-test", CardType.SAMSUNG, "1234-5678-9012-3456",
				new BigDecimal("50000"), "txn-key", PaymentStatus.SUCCESS, null, LocalDateTime.now());

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> payment.fail("잔액 부족"));

			// Assert
			assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
		}

		@Test
		@DisplayName("[fail()] FAILED 상태 -> BAD_REQUEST 예외. 최종 상태에서 전이 불가")
		void failFromFailed() {
			// Arrange
			Payment payment = Payment.reconstruct(1L, 2L, 100L, "PGO-test", CardType.SAMSUNG, "1234-5678-9012-3456",
				new BigDecimal("50000"), "txn-key", PaymentStatus.FAILED, "기존 사유", LocalDateTime.now());

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> payment.fail("새 사유"));

			// Assert
			assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
		}
	}

}
