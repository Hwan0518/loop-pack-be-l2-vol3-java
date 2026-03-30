package com.loopers.coupon.coupontemplate.application.facade;


import com.loopers.coupon.coupontemplate.application.dto.in.AdminCreateCouponTemplateInDto;
import com.loopers.coupon.coupontemplate.application.dto.in.AdminUpdateCouponTemplateInDto;
import com.loopers.coupon.coupontemplate.application.dto.out.AdminCouponTemplateOutDto;
import com.loopers.coupon.coupontemplate.application.service.CouponTemplateCommandService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
@DisplayName("CouponTemplateCommandFacade 테스트")
class CouponTemplateCommandFacadeTest {

	@Mock
	private CouponTemplateCommandService couponTemplateCommandService;

	private CouponTemplateCommandFacade couponTemplateCommandFacade;


	@BeforeEach
	void setUp() {
		couponTemplateCommandFacade = new CouponTemplateCommandFacade(
			couponTemplateCommandService
		);
	}


	@Nested
	@DisplayName("createCouponTemplate() 테스트")
	class CreateCouponTemplateTest {

		@Test
		@DisplayName("[createCouponTemplate()] 유효한 AdminCreateCouponTemplateInDto -> AdminCouponTemplateOutDto 반환. 도메인 모델 생성 후 서비스에 저장 위임")
		void createCouponTemplateSuccess() {
			// Arrange
			LocalDateTime expiredAt = LocalDateTime.now().plusDays(30);
			AdminCreateCouponTemplateInDto inDto = new AdminCreateCouponTemplateInDto(
				"신규 가입 쿠폰", CouponType.FIXED, new BigDecimal("5000"), null, null, expiredAt
			);

			CouponTemplate savedTemplate = CouponTemplate.reconstruct(
				1L, "신규 가입 쿠폰", CouponType.FIXED, new BigDecimal("5000"), null, null, expiredAt, null
			);
			given(couponTemplateCommandService.save(any(CouponTemplate.class))).willReturn(savedTemplate);

			// Act
			AdminCouponTemplateOutDto result = couponTemplateCommandFacade.createCouponTemplate(inDto);

			// Assert
			assertAll(
				() -> assertThat(result.id()).isEqualTo(1L),
				() -> assertThat(result.name()).isEqualTo("신규 가입 쿠폰"),
				() -> assertThat(result.type()).isEqualTo(CouponType.FIXED),
				() -> assertThat(result.value()).isEqualByComparingTo(new BigDecimal("5000")),
				() -> assertThat(result.minOrderAmount()).isNull(),
				() -> assertThat(result.expiredAt()).isEqualTo(expiredAt),
				() -> assertThat(result.deletedAt()).isNull()
			);
			verify(couponTemplateCommandService).save(any(CouponTemplate.class));
		}

	}


	@Nested
	@DisplayName("updateCouponTemplate() 테스트")
	class UpdateCouponTemplateTest {

		@Test
		@DisplayName("[updateCouponTemplate()] 유효한 id + AdminUpdateCouponTemplateInDto -> AdminCouponTemplateOutDto 반환. 서비스에 수정 위임")
		void updateCouponTemplateSuccess() {
			// Arrange
			LocalDateTime newExpiredAt = LocalDateTime.now().plusDays(60);
			AdminUpdateCouponTemplateInDto inDto = new AdminUpdateCouponTemplateInDto(
				"변경된 쿠폰명", newExpiredAt
			);

			CouponTemplate updatedTemplate = CouponTemplate.reconstruct(
				1L, "변경된 쿠폰명", CouponType.FIXED, new BigDecimal("5000"), null, null, newExpiredAt, null
			);
			given(couponTemplateCommandService.update(1L, "변경된 쿠폰명", newExpiredAt)).willReturn(updatedTemplate);

			// Act
			AdminCouponTemplateOutDto result = couponTemplateCommandFacade.updateCouponTemplate(1L, inDto);

			// Assert
			assertAll(
				() -> assertThat(result.id()).isEqualTo(1L),
				() -> assertThat(result.name()).isEqualTo("변경된 쿠폰명"),
				() -> assertThat(result.expiredAt()).isEqualTo(newExpiredAt)
			);
			verify(couponTemplateCommandService).update(1L, "변경된 쿠폰명", newExpiredAt);
		}

	}


	@Nested
	@DisplayName("deleteCouponTemplate() 테스트")
	class DeleteCouponTemplateTest {

		@Test
		@DisplayName("[deleteCouponTemplate()] 유효한 id -> 예외 없이 서비스에 삭제 위임")
		void deleteCouponTemplateSuccess() {
			// Arrange
			willDoNothing().given(couponTemplateCommandService).delete(1L);

			// Act & Assert
			assertDoesNotThrow(() -> couponTemplateCommandFacade.deleteCouponTemplate(1L));
			verify(couponTemplateCommandService).delete(1L);
		}

	}

}
