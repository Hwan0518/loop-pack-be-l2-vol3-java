package com.loopers.coupon.issuedcoupon.application.facade;


import com.loopers.coupon.coupontemplate.application.service.CouponTemplateQueryService;
import com.loopers.coupon.coupontemplate.domain.model.CouponTemplate;
import com.loopers.coupon.coupontemplate.domain.model.enums.CouponType;
import com.loopers.coupon.issuedcoupon.application.dto.out.AdminIssuedCouponOutDto;
import com.loopers.coupon.issuedcoupon.application.dto.out.IssuedCouponOutDto;
import com.loopers.coupon.issuedcoupon.application.service.IssuedCouponQueryService;
import com.loopers.coupon.issuedcoupon.domain.model.IssuedCoupon;
import com.loopers.coupon.issuedcoupon.domain.model.enums.IssuedCouponStatus;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
@DisplayName("IssuedCouponQueryFacade 테스트")
class IssuedCouponQueryFacadeTest {

	@Mock
	private IssuedCouponQueryService issuedCouponQueryService;

	@Mock
	private CouponTemplateQueryService couponTemplateQueryService;

	private IssuedCouponQueryFacade issuedCouponQueryFacade;


	@BeforeEach
	void setUp() {
		issuedCouponQueryFacade = new IssuedCouponQueryFacade(
			issuedCouponQueryService, couponTemplateQueryService
		);
	}


	@Nested
	@DisplayName("getMyIssuedCoupons() 테스트")
	class GetMyIssuedCouponsTest {

		@Test
		@DisplayName("[getMyIssuedCoupons()] 유효한 userId + 발급 쿠폰 존재 -> List<IssuedCouponOutDto> 반환. 목록 조회 → 템플릿 일괄 조회 → DTO 변환")
		void getMyIssuedCouponsSuccess() {
			// Arrange
			Long userId = 100L;
			CouponTemplate template = CouponTemplate.reconstruct(
				1L, "테스트 쿠폰", CouponType.FIXED, new BigDecimal("5000"),
				null, LocalDateTime.now().plusDays(30), null
			);
			IssuedCoupon issuedCoupon = IssuedCoupon.reconstruct(
				10L, 1L, userId, IssuedCouponStatus.AVAILABLE, ZonedDateTime.now()
			);

			given(issuedCouponQueryService.getAllByUserId(userId)).willReturn(List.of(issuedCoupon));
			given(couponTemplateQueryService.getByIdIncludeDeleted(1L)).willReturn(template);

			// Act
			List<IssuedCouponOutDto> result = issuedCouponQueryFacade.getMyIssuedCoupons(userId);

			// Assert
			assertAll(
				() -> assertThat(result).hasSize(1),
				() -> assertThat(result.get(0).issuedCouponId()).isEqualTo(10L),
				() -> assertThat(result.get(0).templateName()).isEqualTo("테스트 쿠폰"),
				() -> assertThat(result.get(0).type()).isEqualTo(CouponType.FIXED),
				() -> assertThat(result.get(0).value()).isEqualByComparingTo(new BigDecimal("5000"))
			);
			verify(issuedCouponQueryService).getAllByUserId(userId);
			verify(couponTemplateQueryService).getByIdIncludeDeleted(1L);
		}


		@Test
		@DisplayName("[getMyIssuedCoupons()] 유효한 userId + 발급 쿠폰 없음 -> 빈 리스트 반환. 템플릿 조회 미호출")
		void getMyIssuedCouponsEmpty() {
			// Arrange
			Long userId = 100L;
			given(issuedCouponQueryService.getAllByUserId(userId)).willReturn(List.of());

			// Act
			List<IssuedCouponOutDto> result = issuedCouponQueryFacade.getMyIssuedCoupons(userId);

			// Assert
			assertThat(result).isEmpty();
			verify(issuedCouponQueryService).getAllByUserId(userId);
		}

	}


	@Nested
	@DisplayName("getIssuedCouponsByTemplateId() 테스트")
	class GetIssuedCouponsByTemplateIdTest {

		@Test
		@DisplayName("[getIssuedCouponsByTemplateId()] 유효한 couponTemplateId -> List<AdminIssuedCouponOutDto> 반환. 템플릿 조회 → 발급 목록 조회 → DTO 변환")
		void getIssuedCouponsByTemplateIdSuccess() {
			// Arrange
			CouponTemplate template = CouponTemplate.reconstruct(
				1L, "테스트 쿠폰", CouponType.FIXED, new BigDecimal("5000"),
				null, LocalDateTime.now().plusDays(30), null
			);
			IssuedCoupon issuedCoupon1 = IssuedCoupon.reconstruct(
				10L, 1L, 100L, IssuedCouponStatus.AVAILABLE, ZonedDateTime.now()
			);
			IssuedCoupon issuedCoupon2 = IssuedCoupon.reconstruct(
				11L, 1L, 200L, IssuedCouponStatus.USED, ZonedDateTime.now()
			);

			given(couponTemplateQueryService.getByIdIncludeDeleted(1L)).willReturn(template);
			given(issuedCouponQueryService.getAllByCouponTemplateId(1L)).willReturn(List.of(issuedCoupon1, issuedCoupon2));

			// Act
			List<AdminIssuedCouponOutDto> result = issuedCouponQueryFacade.getIssuedCouponsByTemplateId(1L);

			// Assert
			assertAll(
				() -> assertThat(result).hasSize(2),
				() -> assertThat(result.get(0).id()).isEqualTo(10L),
				() -> assertThat(result.get(0).couponTemplateId()).isEqualTo(1L),
				() -> assertThat(result.get(0).userId()).isEqualTo(100L),
				() -> assertThat(result.get(1).id()).isEqualTo(11L),
				() -> assertThat(result.get(1).userId()).isEqualTo(200L)
			);
			verify(couponTemplateQueryService).getByIdIncludeDeleted(1L);
			verify(issuedCouponQueryService).getAllByCouponTemplateId(1L);
		}

	}

}
