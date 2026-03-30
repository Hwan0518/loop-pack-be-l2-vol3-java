package com.loopers.coupon.coupontemplate.application.facade;


import com.loopers.coupon.coupontemplate.application.dto.out.AdminCouponTemplateOutDto;
import com.loopers.coupon.coupontemplate.application.dto.out.AdminCouponTemplatePageOutDto;
import com.loopers.coupon.coupontemplate.application.service.CouponTemplateQueryService;
import com.loopers.coupon.coupontemplate.domain.model.CouponTemplate;
import com.loopers.coupon.coupontemplate.domain.model.enums.CouponType;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
@DisplayName("CouponTemplateQueryFacade 테스트")
class CouponTemplateQueryFacadeTest {

	@Mock
	private CouponTemplateQueryService couponTemplateQueryService;

	private CouponTemplateQueryFacade couponTemplateQueryFacade;


	@BeforeEach
	void setUp() {
		couponTemplateQueryFacade = new CouponTemplateQueryFacade(
			couponTemplateQueryService
		);
	}


	@Nested
	@DisplayName("getCouponTemplates() 테스트")
	class GetCouponTemplatesTest {

		@Test
		@DisplayName("[getCouponTemplates()] 유효한 page/size -> AdminCouponTemplatePageOutDto 반환. 서비스에 조회 위임")
		void getCouponTemplatesSuccess() {
			// Arrange
			CouponTemplate template = CouponTemplate.reconstruct(
				1L, "신규 가입 쿠폰", CouponType.FIXED, new BigDecimal("5000"), null,
				null, LocalDateTime.now().plusDays(30), null
			);
			AdminCouponTemplateOutDto outDto = AdminCouponTemplateOutDto.from(template);
			AdminCouponTemplatePageOutDto pageOutDto = new AdminCouponTemplatePageOutDto(
				List.of(outDto), 0, 10, 1L
			);
			given(couponTemplateQueryService.getAll(0, 10)).willReturn(pageOutDto);

			// Act
			AdminCouponTemplatePageOutDto result = couponTemplateQueryFacade.getCouponTemplates(0, 10);

			// Assert
			assertAll(
				() -> assertThat(result.content()).hasSize(1),
				() -> assertThat(result.content().get(0).id()).isEqualTo(1L),
				() -> assertThat(result.content().get(0).name()).isEqualTo("신규 가입 쿠폰"),
				() -> assertThat(result.page()).isZero(),
				() -> assertThat(result.size()).isEqualTo(10),
				() -> assertThat(result.totalElements()).isEqualTo(1L)
			);
			verify(couponTemplateQueryService).getAll(0, 10);
		}

	}

}
