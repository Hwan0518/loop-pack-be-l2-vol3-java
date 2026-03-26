package com.loopers.coupon.coupontemplate.domain.model;


import com.loopers.coupon.coupontemplate.domain.model.enums.CouponType;
import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;


@DisplayName("CouponTemplate 도메인 모델 테스트")
class CouponTemplateTest {

	private final LocalDateTime futureExpiredAt = LocalDateTime.now().plusDays(30);


	@Nested
	@DisplayName("create()")
	class CreateTest {

		@Test
		@DisplayName("[create()] 유효한 FIXED 쿠폰 -> id=null, deletedAt=null로 생성 성공")
		void createFixedSuccess() {
			// Act
			CouponTemplate template = CouponTemplate.create(
				"신규가입 쿠폰", CouponType.FIXED, new BigDecimal("5000"),
				new BigDecimal("10000"), null, futureExpiredAt
			);

			// Assert
			assertAll(
				() -> assertThat(template.getId()).isNull(),
				() -> assertThat(template.getName()).isEqualTo("신규가입 쿠폰"),
				() -> assertThat(template.getType()).isEqualTo(CouponType.FIXED),
				() -> assertThat(template.getValue()).isEqualByComparingTo(new BigDecimal("5000")),
				() -> assertThat(template.getMinOrderAmount()).isEqualByComparingTo(new BigDecimal("10000")),
				() -> assertThat(template.getExpiredAt()).isEqualTo(futureExpiredAt),
				() -> assertThat(template.getDeletedAt()).isNull()
			);
		}


		@Test
		@DisplayName("[create()] 유효한 RATE 쿠폰 -> 생성 성공")
		void createRateSuccess() {
			// Act
			CouponTemplate template = CouponTemplate.create(
				"10% 할인 쿠폰", CouponType.RATE, new BigDecimal("10"),
				null, null, futureExpiredAt
			);

			// Assert
			assertAll(
				() -> assertThat(template.getType()).isEqualTo(CouponType.RATE),
				() -> assertThat(template.getValue()).isEqualByComparingTo(new BigDecimal("10")),
				() -> assertThat(template.getMinOrderAmount()).isNull()
			);
		}


		@Test
		@DisplayName("[create()] type null -> NullPointerException")
		void createWithNullType() {
			// Act & Assert
			assertThrows(NullPointerException.class,
				() -> CouponTemplate.create("쿠폰", null, new BigDecimal("5000"),
					null, null, futureExpiredAt));
		}


		@Test
		@DisplayName("[create()] name null -> INVALID_COUPON_NAME 예외")
		void createWithNullName() {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> CouponTemplate.create(null, CouponType.FIXED, new BigDecimal("5000"),
					null, null, futureExpiredAt));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_COUPON_NAME),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_COUPON_NAME.getMessage())
			);
		}


		@Test
		@DisplayName("[create()] name 빈 문자열 -> INVALID_COUPON_NAME 예외")
		void createWithBlankName() {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> CouponTemplate.create("  ", CouponType.FIXED, new BigDecimal("5000"),
					null, null, futureExpiredAt));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_COUPON_NAME),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_COUPON_NAME.getMessage())
			);
		}


		@Test
		@DisplayName("[create()] name 101자 -> INVALID_COUPON_NAME 예외")
		void createWithTooLongName() {
			// Arrange
			String longName = "a".repeat(101);

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> CouponTemplate.create(longName, CouponType.FIXED, new BigDecimal("5000"),
					null, null, futureExpiredAt));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_COUPON_NAME),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_COUPON_NAME.getMessage())
			);
		}


		@Test
		@DisplayName("[create()] name 100자 -> 생성 성공")
		void createWithMaxLengthName() {
			// Arrange
			String maxName = "a".repeat(100);

			// Act & Assert
			assertDoesNotThrow(() -> CouponTemplate.create(maxName, CouponType.FIXED, new BigDecimal("5000"),
				null, null, futureExpiredAt));
		}


		@Test
		@DisplayName("[create()] value null -> INVALID_COUPON_VALUE 예외")
		void createWithNullValue() {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> CouponTemplate.create("쿠폰", CouponType.FIXED, null,
					null, null, futureExpiredAt));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_COUPON_VALUE),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_COUPON_VALUE.getMessage())
			);
		}


		@Test
		@DisplayName("[create()] value 0 -> INVALID_COUPON_VALUE 예외")
		void createWithZeroValue() {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> CouponTemplate.create("쿠폰", CouponType.FIXED, BigDecimal.ZERO,
					null, null, futureExpiredAt));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_COUPON_VALUE),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_COUPON_VALUE.getMessage())
			);
		}


		@Test
		@DisplayName("[create()] RATE 타입 value 100 초과 -> INVALID_COUPON_VALUE 예외")
		void createRateWithValueOver100() {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> CouponTemplate.create("쿠폰", CouponType.RATE, new BigDecimal("101"),
					null, null, futureExpiredAt));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_COUPON_VALUE),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_COUPON_VALUE.getMessage())
			);
		}


		@Test
		@DisplayName("[create()] RATE 타입 value 100 -> 생성 성공 (경계값)")
		void createRateWithValue100() {
			// Act & Assert
			assertDoesNotThrow(() -> CouponTemplate.create("쿠폰", CouponType.RATE, new BigDecimal("100"),
				null, null, futureExpiredAt));
		}


		@Test
		@DisplayName("[create()] expiredAt null -> INVALID_COUPON_EXPIRED_AT 예외")
		void createWithNullExpiredAt() {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> CouponTemplate.create("쿠폰", CouponType.FIXED, new BigDecimal("5000"),
					null, null, null));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_COUPON_EXPIRED_AT),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_COUPON_EXPIRED_AT.getMessage())
			);
		}


		@Test
		@DisplayName("[create()] expiredAt 과거 시각 -> INVALID_COUPON_EXPIRED_AT 예외")
		void createWithPastExpiredAt() {
			// Arrange
			LocalDateTime past = LocalDateTime.now().minusDays(1);

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> CouponTemplate.create("쿠폰", CouponType.FIXED, new BigDecimal("5000"),
					null, null, past));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_COUPON_EXPIRED_AT),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_COUPON_EXPIRED_AT.getMessage())
			);
		}


		@Test
		@DisplayName("[create()] FIXED 타입 minOrderAmount < value -> COUPON_VALUE_EXCEEDS_MIN_ORDER_AMOUNT 예외")
		void createFixedWithMinOrderAmountLessThanValue() {
			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> CouponTemplate.create("쿠폰", CouponType.FIXED, new BigDecimal("5000"),
					new BigDecimal("4999"), null, futureExpiredAt));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.COUPON_VALUE_EXCEEDS_MIN_ORDER_AMOUNT),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.COUPON_VALUE_EXCEEDS_MIN_ORDER_AMOUNT.getMessage())
			);
		}


		@Test
		@DisplayName("[create()] FIXED 타입 minOrderAmount == value -> 생성 성공 (경계값)")
		void createFixedWithMinOrderAmountEqualsValue() {
			// Act & Assert
			assertDoesNotThrow(() -> CouponTemplate.create("쿠폰", CouponType.FIXED, new BigDecimal("5000"),
				new BigDecimal("5000"), null, futureExpiredAt));
		}


		@Test
		@DisplayName("[create()] FIXED 타입 minOrderAmount null -> 생성 성공 (조건 없음)")
		void createFixedWithNullMinOrderAmount() {
			// Act & Assert
			assertDoesNotThrow(() -> CouponTemplate.create("쿠폰", CouponType.FIXED, new BigDecimal("5000"),
				null, null, futureExpiredAt));
		}

	}


	@Nested
	@DisplayName("changeName()")
	class ChangeNameTest {

		@Test
		@DisplayName("[changeName()] 유효한 이름 -> 이름 변경 성공")
		void changeNameSuccess() {
			// Arrange
			CouponTemplate template = CouponTemplate.create(
				"원래 이름", CouponType.FIXED, new BigDecimal("5000"), null, null, futureExpiredAt
			);

			// Act
			template.changeName("변경된 이름");

			// Assert
			assertThat(template.getName()).isEqualTo("변경된 이름");
		}


		@Test
		@DisplayName("[changeName()] 빈 이름 -> INVALID_COUPON_NAME 예외")
		void changeNameBlank() {
			// Arrange
			CouponTemplate template = CouponTemplate.create(
				"원래 이름", CouponType.FIXED, new BigDecimal("5000"), null, null, futureExpiredAt
			);

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> template.changeName(""));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_COUPON_NAME),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_COUPON_NAME.getMessage())
			);
		}

	}


	@Nested
	@DisplayName("changeExpiredAt()")
	class ChangeExpiredAtTest {

		@Test
		@DisplayName("[changeExpiredAt()] 미래 시각 -> 만료일 변경 성공")
		void changeExpiredAtSuccess() {
			// Arrange
			CouponTemplate template = CouponTemplate.create(
				"쿠폰", CouponType.FIXED, new BigDecimal("5000"), null, null, futureExpiredAt
			);
			LocalDateTime newExpiredAt = LocalDateTime.now().plusDays(60);

			// Act
			template.changeExpiredAt(newExpiredAt);

			// Assert
			assertThat(template.getExpiredAt()).isEqualTo(newExpiredAt);
		}


		@Test
		@DisplayName("[changeExpiredAt()] 과거 시각 -> INVALID_COUPON_EXPIRED_AT 예외")
		void changeExpiredAtPast() {
			// Arrange
			CouponTemplate template = CouponTemplate.create(
				"쿠폰", CouponType.FIXED, new BigDecimal("5000"), null, null, futureExpiredAt
			);

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> template.changeExpiredAt(LocalDateTime.now().minusDays(1)));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_COUPON_EXPIRED_AT),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_COUPON_EXPIRED_AT.getMessage())
			);
		}

	}


	@Nested
	@DisplayName("delete() / isDeleted()")
	class DeleteTest {

		@Test
		@DisplayName("[delete()] 미삭제 템플릿 -> deletedAt 설정됨")
		void deleteSuccess() {
			// Arrange
			CouponTemplate template = CouponTemplate.create(
				"쿠폰", CouponType.FIXED, new BigDecimal("5000"), null, null, futureExpiredAt
			);

			// Act
			template.delete();

			// Assert
			assertAll(
				() -> assertThat(template.isDeleted()).isTrue(),
				() -> assertThat(template.getDeletedAt()).isNotNull()
			);
		}


		@Test
		@DisplayName("[isDeleted()] deletedAt null -> false")
		void isDeletedFalse() {
			// Arrange
			CouponTemplate template = CouponTemplate.create(
				"쿠폰", CouponType.FIXED, new BigDecimal("5000"), null, null, futureExpiredAt
			);

			// Assert
			assertThat(template.isDeleted()).isFalse();
		}

	}


	@Nested
	@DisplayName("calculateDiscount()")
	class CalculateDiscountTest {

		@Test
		@DisplayName("[calculateDiscount()] FIXED 5000원 쿠폰, 30000원 주문 -> 5000원 할인")
		void calculateDiscountFixed() {
			// Arrange
			CouponTemplate template = CouponTemplate.create(
				"5000원 할인", CouponType.FIXED, new BigDecimal("5000"), null, null, futureExpiredAt
			);

			// Act
			BigDecimal discount = template.calculateDiscount(new BigDecimal("30000"));

			// Assert
			assertThat(discount).isEqualByComparingTo(new BigDecimal("5000"));
		}


		@Test
		@DisplayName("[calculateDiscount()] RATE 10% 쿠폰, 25000원 주문 -> 2500원 할인")
		void calculateDiscountRate() {
			// Arrange
			CouponTemplate template = CouponTemplate.create(
				"10% 할인", CouponType.RATE, new BigDecimal("10"), null, null, futureExpiredAt
			);

			// Act
			BigDecimal discount = template.calculateDiscount(new BigDecimal("25000"));

			// Assert
			assertThat(discount).isEqualByComparingTo(new BigDecimal("2500"));
		}

	}

}
