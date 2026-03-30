package com.loopers.coupon.issuedcoupon.application.facade;


import com.loopers.coupon.coupontemplate.application.service.CouponTemplateQueryService;
import com.loopers.coupon.coupontemplate.domain.model.CouponTemplate;
import com.loopers.coupon.coupontemplate.domain.model.enums.CouponType;
import com.loopers.coupon.issuedcoupon.application.dto.out.CouponApplyResult;

import com.loopers.coupon.issuedcoupon.application.service.IssuedCouponCommandService;
import com.loopers.coupon.issuedcoupon.domain.model.IssuedCoupon;
import com.loopers.coupon.issuedcoupon.domain.model.enums.IssuedCouponStatus;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
@DisplayName("IssuedCouponCommandFacade 테스트")
class IssuedCouponCommandFacadeTest {

	@Mock
	private IssuedCouponCommandService issuedCouponCommandService;

	@Mock
	private CouponTemplateQueryService couponTemplateQueryService;

	private IssuedCouponCommandFacade issuedCouponCommandFacade;


	@BeforeEach
	void setUp() {
		issuedCouponCommandFacade = new IssuedCouponCommandFacade(
			issuedCouponCommandService,
			couponTemplateQueryService
		);
	}


	// 테스트용 활성 쿠폰 템플릿
	private CouponTemplate activeTemplate() {
		return CouponTemplate.reconstruct(
			1L, "테스트 쿠폰", CouponType.FIXED, new BigDecimal("5000"),
			null, null, LocalDateTime.now().plusDays(30), null
		);
	}


	@Nested
	@DisplayName("applyToCoupon() 테스트")
	class ApplyToCouponTest {

		@Test
		@DisplayName("[applyToCoupon()] 유효한 쿠폰 적용 -> CouponApplyResult 반환. 비관적 락 조회 -> 템플릿 조회 -> 검증 -> 상태 변경 -> 할인 계산")
		void applyToCouponSuccess() {
			// Arrange
			IssuedCoupon issuedCoupon = IssuedCoupon.reconstruct(
				10L, 1L, 100L, IssuedCouponStatus.AVAILABLE, ZonedDateTime.now()
			);
			CouponTemplate template = activeTemplate();
			CouponApplyResult applyResult = new CouponApplyResult(
				10L, new BigDecimal("5000"), "테스트 쿠폰", "FIXED", new BigDecimal("5000")
			);

			given(issuedCouponCommandService.getByIdForUpdate(10L)).willReturn(issuedCoupon);
			given(couponTemplateQueryService.getByIdIncludeDeleted(1L)).willReturn(template);
			given(issuedCouponCommandService.applyToCoupon(issuedCoupon, 100L, new BigDecimal("30000"), template))
				.willReturn(applyResult);

			// Act
			CouponApplyResult result = issuedCouponCommandFacade.applyToCoupon(10L, 100L, new BigDecimal("30000"));

			// Assert
			assertAll(
				() -> assertThat(result.issuedCouponId()).isEqualTo(10L),
				() -> assertThat(result.discountAmount()).isEqualByComparingTo(new BigDecimal("5000")),
				() -> assertThat(result.couponSnapshotName()).isEqualTo("테스트 쿠폰"),
				() -> assertThat(result.couponSnapshotType()).isEqualTo("FIXED"),
				() -> assertThat(result.couponSnapshotValue()).isEqualByComparingTo(new BigDecimal("5000"))
			);
			verify(issuedCouponCommandService).getByIdForUpdate(10L);
			verify(couponTemplateQueryService).getByIdIncludeDeleted(1L);
			verify(issuedCouponCommandService).applyToCoupon(issuedCoupon, 100L, new BigDecimal("30000"), template);
		}

	}

}
