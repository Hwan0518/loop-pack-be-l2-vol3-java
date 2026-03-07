package com.loopers.coupon.issuedcoupon.application.service;


import com.loopers.coupon.coupontemplate.domain.model.CouponTemplate;
import com.loopers.coupon.coupontemplate.domain.model.enums.CouponType;
import com.loopers.coupon.issuedcoupon.application.dto.out.CouponApplyResult;
import com.loopers.coupon.issuedcoupon.application.port.out.cache.CouponIssueDuplicateGuard;
import com.loopers.coupon.issuedcoupon.domain.model.IssuedCoupon;
import com.loopers.coupon.issuedcoupon.domain.model.enums.IssuedCouponStatus;
import com.loopers.coupon.issuedcoupon.domain.repository.IssuedCouponCommandRepository;
import com.loopers.coupon.issuedcoupon.domain.repository.IssuedCouponQueryRepository;
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
import java.time.ZonedDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
@DisplayName("IssuedCouponCommandService 단위 테스트")
class IssuedCouponCommandServiceTest {

	@Mock
	private IssuedCouponCommandRepository issuedCouponCommandRepository;
	@Mock
	private IssuedCouponQueryRepository issuedCouponQueryRepository;
	@Mock
	private CouponIssueDuplicateGuard couponIssueDuplicateGuard;

	private IssuedCouponCommandService issuedCouponCommandService;


	@BeforeEach
	void setUp() {
		issuedCouponCommandService = new IssuedCouponCommandService(
			issuedCouponCommandRepository, issuedCouponQueryRepository, couponIssueDuplicateGuard
		);
	}


	// 테스트 헬퍼 — 활성 템플릿
	private CouponTemplate activeTemplate() {
		return CouponTemplate.reconstruct(
			1L, "10% 할인", CouponType.RATE, new BigDecimal("10"),
			null, LocalDateTime.now().plusDays(30), null
		);
	}

	// 테스트 헬퍼 — 만료된 템플릿
	private CouponTemplate expiredTemplate() {
		return CouponTemplate.reconstruct(
			1L, "만료 쿠폰", CouponType.RATE, new BigDecimal("10"),
			null, LocalDateTime.now().minusDays(1), null
		);
	}

	// 테스트 헬퍼 — 최소 주문 금액 있는 템플릿
	private CouponTemplate templateWithMinOrder(BigDecimal minOrderAmount) {
		return CouponTemplate.reconstruct(
			1L, "최소금액 쿠폰", CouponType.FIXED, new BigDecimal("3000"),
			minOrderAmount, LocalDateTime.now().plusDays(30), null
		);
	}


	@Nested
	@DisplayName("validateNotDuplicateIssue()")
	class ValidateNotDuplicateIssueTest {

		@Test
		@DisplayName("[validateNotDuplicateIssue()] 캐시 최초 + DB 미존재 -> 예외 없이 통과. 1차 캐시, 2차 DB 모두 통과")
		void firstRequestPasses() {
			// Arrange
			given(couponIssueDuplicateGuard.tryAcquire(100L, 1L)).willReturn(true);
			given(issuedCouponQueryRepository.existsByUserIdAndCouponTemplateId(100L, 1L)).willReturn(false);

			// Act & Assert
			assertDoesNotThrow(() -> issuedCouponCommandService.validateNotDuplicateIssue(100L, 1L));
			verify(couponIssueDuplicateGuard).tryAcquire(100L, 1L);
			verify(issuedCouponQueryRepository).existsByUserIdAndCouponTemplateId(100L, 1L);
		}


		@Test
		@DisplayName("[validateNotDuplicateIssue()] 캐시 중복 요청 -> COUPON_ISSUE_DUPLICATED 예외. 1차 캐시에서 차단")
		void duplicateRequestByCacheThrows() {
			// Arrange
			given(couponIssueDuplicateGuard.tryAcquire(100L, 1L)).willReturn(false);

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> issuedCouponCommandService.validateNotDuplicateIssue(100L, 1L));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.COUPON_ISSUE_DUPLICATED),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.COUPON_ISSUE_DUPLICATED.getMessage())
			);
		}


		@Test
		@DisplayName("[validateNotDuplicateIssue()] 캐시 TTL 만료 + DB 존재 -> COUPON_ISSUE_DUPLICATED 예외. 2차 DB에서 차단")
		void duplicateRequestByDbFallbackThrows() {
			// Arrange — 캐시 TTL 만료로 tryAcquire가 true 반환, 하지만 DB에는 이미 존재
			given(couponIssueDuplicateGuard.tryAcquire(100L, 1L)).willReturn(true);
			given(issuedCouponQueryRepository.existsByUserIdAndCouponTemplateId(100L, 1L)).willReturn(true);

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> issuedCouponCommandService.validateNotDuplicateIssue(100L, 1L));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.COUPON_ISSUE_DUPLICATED),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.COUPON_ISSUE_DUPLICATED.getMessage())
			);
		}

	}


	@Nested
	@DisplayName("save()")
	class SaveTest {

		@Test
		@DisplayName("[save()] 유효한 IssuedCoupon -> 저장 후 반환")
		void saveSuccess() {
			// Arrange
			IssuedCoupon issuedCoupon = IssuedCoupon.create(1L, 100L);
			IssuedCoupon saved = IssuedCoupon.reconstruct(10L, 1L, 100L, IssuedCouponStatus.AVAILABLE, ZonedDateTime.now());
			given(issuedCouponCommandRepository.save(any(IssuedCoupon.class))).willReturn(saved);

			// Act
			IssuedCoupon result = issuedCouponCommandService.save(issuedCoupon);

			// Assert
			assertAll(
				() -> assertThat(result.getId()).isEqualTo(10L),
				() -> verify(issuedCouponCommandRepository).save(issuedCoupon)
			);
		}

	}


	@Nested
	@DisplayName("getByIdForUpdate()")
	class GetByIdForUpdateTest {

		@Test
		@DisplayName("[getByIdForUpdate()] 존재하는 발급 쿠폰 ID -> IssuedCoupon 반환. 비관적 쓰기 락으로 조회")
		void getByIdForUpdateSuccess() {
			// Arrange
			IssuedCoupon issuedCoupon = IssuedCoupon.reconstruct(1L, 1L, 100L, IssuedCouponStatus.AVAILABLE, ZonedDateTime.now());
			given(issuedCouponQueryRepository.findByIdForUpdate(1L)).willReturn(Optional.of(issuedCoupon));

			// Act
			IssuedCoupon result = issuedCouponCommandService.getByIdForUpdate(1L);

			// Assert
			assertAll(
				() -> assertThat(result.getId()).isEqualTo(1L),
				() -> verify(issuedCouponQueryRepository).findByIdForUpdate(1L)
			);
		}


		@Test
		@DisplayName("[getByIdForUpdate()] 존재하지 않는 발급 쿠폰 ID -> ISSUED_COUPON_NOT_FOUND 예외")
		void getByIdForUpdateNotFound() {
			// Arrange
			given(issuedCouponQueryRepository.findByIdForUpdate(999L)).willReturn(Optional.empty());

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> issuedCouponCommandService.getByIdForUpdate(999L));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.ISSUED_COUPON_NOT_FOUND),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.ISSUED_COUPON_NOT_FOUND.getMessage())
			);
		}

	}


	@Nested
	@DisplayName("applyToCoupon()")
	class ApplyToCouponTest {

		@Test
		@DisplayName("[applyToCoupon()] 유효한 쿠폰 적용 -> CouponApplyResult 반환, 상태 USED로 변경. Facade에서 비관적 락으로 조회된 쿠폰 전달")
		void applySuccess() {
			// Arrange
			IssuedCoupon issuedCoupon = IssuedCoupon.reconstruct(1L, 1L, 100L, IssuedCouponStatus.AVAILABLE, ZonedDateTime.now());
			given(issuedCouponCommandRepository.save(any(IssuedCoupon.class))).willAnswer(invocation -> invocation.getArgument(0));

			// Act
			CouponApplyResult result = issuedCouponCommandService.applyToCoupon(
				issuedCoupon, 100L, new BigDecimal("30000"), activeTemplate()
			);

			// Assert
			assertAll(
				() -> assertThat(result.issuedCouponId()).isEqualTo(1L),
				() -> assertThat(result.discountAmount()).isEqualByComparingTo(new BigDecimal("3000")),
				() -> assertThat(issuedCoupon.getStatus()).isEqualTo(IssuedCouponStatus.USED),
				() -> verify(issuedCouponCommandRepository).save(issuedCoupon)
			);
		}


		@Test
		@DisplayName("[applyToCoupon()] 쿠폰 소유자가 아닌 userId -> COUPON_NOT_OWNED_BY_USER 예외")
		void applyNotOwned() {
			// Arrange
			IssuedCoupon issuedCoupon = IssuedCoupon.reconstruct(1L, 1L, 100L, IssuedCouponStatus.AVAILABLE, ZonedDateTime.now());

			// Act — userId 999L은 소유자(100L)가 아님
			CoreException exception = assertThrows(CoreException.class,
				() -> issuedCouponCommandService.applyToCoupon(
					issuedCoupon, 999L, new BigDecimal("30000"), activeTemplate()
				));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.COUPON_NOT_OWNED_BY_USER),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.COUPON_NOT_OWNED_BY_USER.getMessage())
			);
		}


		@Test
		@DisplayName("[applyToCoupon()] 이미 사용된 쿠폰 -> COUPON_ALREADY_USED 예외")
		void applyAlreadyUsed() {
			// Arrange
			IssuedCoupon issuedCoupon = IssuedCoupon.reconstruct(1L, 1L, 100L, IssuedCouponStatus.USED, ZonedDateTime.now());

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> issuedCouponCommandService.applyToCoupon(
					issuedCoupon, 100L, new BigDecimal("30000"), activeTemplate()
				));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.COUPON_ALREADY_USED),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.COUPON_ALREADY_USED.getMessage())
			);
		}


		@Test
		@DisplayName("[applyToCoupon()] 만료된 쿠폰 템플릿 -> COUPON_EXPIRED 예외")
		void applyExpired() {
			// Arrange
			IssuedCoupon issuedCoupon = IssuedCoupon.reconstruct(1L, 1L, 100L, IssuedCouponStatus.AVAILABLE, ZonedDateTime.now());

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> issuedCouponCommandService.applyToCoupon(
					issuedCoupon, 100L, new BigDecimal("30000"), expiredTemplate()
				));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.COUPON_EXPIRED),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.COUPON_EXPIRED.getMessage())
			);
		}


		@Test
		@DisplayName("[applyToCoupon()] totalPrice < minOrderAmount -> COUPON_MIN_ORDER_AMOUNT_NOT_MET 예외")
		void applyMinOrderAmountNotMet() {
			// Arrange
			IssuedCoupon issuedCoupon = IssuedCoupon.reconstruct(1L, 1L, 100L, IssuedCouponStatus.AVAILABLE, ZonedDateTime.now());

			// Act — totalPrice 5000 < minOrderAmount 10000
			CoreException exception = assertThrows(CoreException.class,
				() -> issuedCouponCommandService.applyToCoupon(
					issuedCoupon, 100L, new BigDecimal("5000"), templateWithMinOrder(new BigDecimal("10000"))
				));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.COUPON_MIN_ORDER_AMOUNT_NOT_MET),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.COUPON_MIN_ORDER_AMOUNT_NOT_MET.getMessage())
			);
		}


		@Test
		@DisplayName("[applyToCoupon()] totalPrice == minOrderAmount -> 적용 성공 (경계값)")
		void applyMinOrderAmountEqual() {
			// Arrange
			IssuedCoupon issuedCoupon = IssuedCoupon.reconstruct(1L, 1L, 100L, IssuedCouponStatus.AVAILABLE, ZonedDateTime.now());
			given(issuedCouponCommandRepository.save(any(IssuedCoupon.class))).willAnswer(invocation -> invocation.getArgument(0));

			// Act — totalPrice == minOrderAmount == 10000
			assertDoesNotThrow(() -> issuedCouponCommandService.applyToCoupon(
				issuedCoupon, 100L, new BigDecimal("10000"), templateWithMinOrder(new BigDecimal("10000"))
			));
		}

	}

}
