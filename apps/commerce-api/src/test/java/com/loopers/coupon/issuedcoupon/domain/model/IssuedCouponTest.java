package com.loopers.coupon.issuedcoupon.domain.model;


import com.loopers.coupon.coupontemplate.domain.model.CouponTemplate;
import com.loopers.coupon.coupontemplate.domain.model.enums.CouponType;
import com.loopers.coupon.issuedcoupon.domain.model.enums.CouponStatusView;
import com.loopers.coupon.issuedcoupon.domain.model.enums.IssuedCouponStatus;
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


@DisplayName("IssuedCoupon 도메인 모델 테스트")
class IssuedCouponTest {

	// 테스트 헬퍼 — 미만료 활성 템플릿
	private CouponTemplate activeTemplate() {
		return CouponTemplate.reconstruct(
			1L, "테스트 쿠폰", CouponType.FIXED, new BigDecimal("1000"),
			null, LocalDateTime.now().plusDays(30), null
		);
	}

	// 테스트 헬퍼 — 만료된 템플릿 (expiredAt < now)
	private CouponTemplate expiredTemplate() {
		return CouponTemplate.reconstruct(
			1L, "만료 쿠폰", CouponType.FIXED, new BigDecimal("1000"),
			null, LocalDateTime.now().minusDays(1), null
		);
	}

	// 테스트 헬퍼 — 소프트 삭제된 템플릿
	private CouponTemplate deletedTemplate() {
		return CouponTemplate.reconstruct(
			1L, "삭제 쿠폰", CouponType.FIXED, new BigDecimal("1000"),
			null, LocalDateTime.now().plusDays(30), ZonedDateTime.now()
		);
	}


	@Nested
	@DisplayName("create()")
	class CreateTest {

		@Test
		@DisplayName("[create()] 유효한 입력 -> id=null, status=AVAILABLE로 생성 성공")
		void createSuccess() {
			// Act
			IssuedCoupon issuedCoupon = IssuedCoupon.create(1L, 100L);

			// Assert
			assertAll(
				() -> assertThat(issuedCoupon.getId()).isNull(),
				() -> assertThat(issuedCoupon.getCouponTemplateId()).isEqualTo(1L),
				() -> assertThat(issuedCoupon.getUserId()).isEqualTo(100L),
				() -> assertThat(issuedCoupon.getStatus()).isEqualTo(IssuedCouponStatus.AVAILABLE)
			);
		}


		@Test
		@DisplayName("[create()] couponTemplateId null -> NullPointerException")
		void createWithNullTemplateId() {
			// Act & Assert
			assertThrows(NullPointerException.class, () -> IssuedCoupon.create(null, 100L));
		}


		@Test
		@DisplayName("[create()] userId null -> NullPointerException")
		void createWithNullUserId() {
			// Act & Assert
			assertThrows(NullPointerException.class, () -> IssuedCoupon.create(1L, null));
		}

	}


	@Nested
	@DisplayName("isExpired()")
	class IsExpiredTest {

		@Test
		@DisplayName("[isExpired()] 활성 템플릿 (expiredAt 미래) -> false")
		void notExpiredWithActiveTemplate() {
			// Arrange
			IssuedCoupon issuedCoupon = IssuedCoupon.create(1L, 100L);

			// Act & Assert
			assertThat(issuedCoupon.isExpired(activeTemplate())).isFalse();
		}


		@Test
		@DisplayName("[isExpired()] 만료된 템플릿 (expiredAt 과거) -> true")
		void expiredWithPastExpiredAt() {
			// Arrange
			IssuedCoupon issuedCoupon = IssuedCoupon.create(1L, 100L);

			// Act & Assert
			assertThat(issuedCoupon.isExpired(expiredTemplate())).isTrue();
		}


		@Test
		@DisplayName("[isExpired()] 소프트 삭제된 템플릿 -> true")
		void expiredWithDeletedTemplate() {
			// Arrange
			IssuedCoupon issuedCoupon = IssuedCoupon.create(1L, 100L);

			// Act & Assert
			assertThat(issuedCoupon.isExpired(deletedTemplate())).isTrue();
		}

	}


	@Nested
	@DisplayName("getStatusView()")
	class GetStatusViewTest {

		@Test
		@DisplayName("[getStatusView()] 활성 템플릿 + AVAILABLE 상태 -> AVAILABLE")
		void availableStatus() {
			// Arrange
			IssuedCoupon issuedCoupon = IssuedCoupon.create(1L, 100L);

			// Act
			CouponStatusView statusView = issuedCoupon.getStatusView(activeTemplate());

			// Assert
			assertThat(statusView).isEqualTo(CouponStatusView.AVAILABLE);
		}


		@Test
		@DisplayName("[getStatusView()] 활성 템플릿 + USED 상태 -> USED")
		void usedStatus() {
			// Arrange
			IssuedCoupon issuedCoupon = IssuedCoupon.reconstruct(1L, 1L, 100L, IssuedCouponStatus.USED, null);

			// Act
			CouponStatusView statusView = issuedCoupon.getStatusView(activeTemplate());

			// Assert
			assertThat(statusView).isEqualTo(CouponStatusView.USED);
		}


		@Test
		@DisplayName("[getStatusView()] 만료된 템플릿 + AVAILABLE 상태 -> EXPIRED (만료 우선)")
		void expiredTemplateAvailableStatus() {
			// Arrange
			IssuedCoupon issuedCoupon = IssuedCoupon.create(1L, 100L);

			// Act
			CouponStatusView statusView = issuedCoupon.getStatusView(expiredTemplate());

			// Assert
			assertThat(statusView).isEqualTo(CouponStatusView.EXPIRED);
		}


		@Test
		@DisplayName("[getStatusView()] 만료된 템플릿 + USED 상태 -> EXPIRED (만료 우선)")
		void expiredTemplateUsedStatus() {
			// Arrange
			IssuedCoupon issuedCoupon = IssuedCoupon.reconstruct(1L, 1L, 100L, IssuedCouponStatus.USED, null);

			// Act
			CouponStatusView statusView = issuedCoupon.getStatusView(expiredTemplate());

			// Assert
			assertThat(statusView).isEqualTo(CouponStatusView.EXPIRED);
		}


		@Test
		@DisplayName("[getStatusView()] 삭제된 템플릿 + AVAILABLE 상태 -> EXPIRED (삭제 = 만료)")
		void deletedTemplateAvailableStatus() {
			// Arrange
			IssuedCoupon issuedCoupon = IssuedCoupon.create(1L, 100L);

			// Act
			CouponStatusView statusView = issuedCoupon.getStatusView(deletedTemplate());

			// Assert
			assertThat(statusView).isEqualTo(CouponStatusView.EXPIRED);
		}

	}


	@Nested
	@DisplayName("use()")
	class UseTest {

		@Test
		@DisplayName("[use()] AVAILABLE 상태 -> status USED로 변경")
		void useSuccess() {
			// Arrange
			IssuedCoupon issuedCoupon = IssuedCoupon.create(1L, 100L);

			// Act
			issuedCoupon.use();

			// Assert
			assertThat(issuedCoupon.getStatus()).isEqualTo(IssuedCouponStatus.USED);
		}

	}


	@Nested
	@DisplayName("restore()")
	class RestoreTest {

		@Test
		@DisplayName("[restore()] USED 상태 -> status AVAILABLE로 복원 성공")
		void restoreFromUsedSuccess() {
			// Arrange
			IssuedCoupon issuedCoupon = IssuedCoupon.reconstruct(1L, 1L, 100L, IssuedCouponStatus.USED, null);

			// Act
			issuedCoupon.restore();

			// Assert
			assertThat(issuedCoupon.getStatus()).isEqualTo(IssuedCouponStatus.AVAILABLE);
		}


		@Test
		@DisplayName("[restore()] AVAILABLE 상태 -> BAD_REQUEST 예외. USED 상태에서만 복원 가능")
		void restoreFromAvailableFails() {
			// Arrange
			IssuedCoupon issuedCoupon = IssuedCoupon.create(1L, 100L);

			// Act
			CoreException exception = assertThrows(CoreException.class,
				issuedCoupon::restore);

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.BAD_REQUEST.getMessage())
			);
		}

	}

}
