package com.loopers.coupon.coupontemplate.application.service;


import com.loopers.coupon.coupontemplate.application.dto.out.AdminCouponTemplatePageOutDto;
import com.loopers.coupon.coupontemplate.domain.model.CouponTemplate;
import com.loopers.coupon.coupontemplate.domain.model.enums.CouponType;
import com.loopers.coupon.coupontemplate.domain.repository.CouponTemplateQueryRepository;
import com.loopers.coupon.coupontemplate.domain.repository.vo.PageCriteria;
import com.loopers.coupon.coupontemplate.domain.repository.vo.PageResult;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.*;


@ExtendWith(MockitoExtension.class)
@DisplayName("CouponTemplateQueryService 단위 테스트")
class CouponTemplateQueryServiceTest {

	@Mock
	private CouponTemplateQueryRepository couponTemplateQueryRepository;

	private CouponTemplateQueryService couponTemplateQueryService;

	private final LocalDateTime futureExpiredAt = LocalDateTime.now().plusDays(30);


	@BeforeEach
	void setUp() {
		couponTemplateQueryService = new CouponTemplateQueryService(couponTemplateQueryRepository);
	}


	// 테스트 헬퍼
	private CouponTemplate activeTemplate(Long id) {
		return CouponTemplate.reconstruct(
			id, "테스트 쿠폰", CouponType.FIXED, new BigDecimal("5000"),
			null, futureExpiredAt, null
		);
	}

	private CouponTemplate deletedTemplate(Long id) {
		return CouponTemplate.reconstruct(
			id, "삭제 쿠폰", CouponType.FIXED, new BigDecimal("5000"),
			null, futureExpiredAt, ZonedDateTime.now()
		);
	}


	@Nested
	@DisplayName("getById()")
	class GetByIdTest {

		@Test
		@DisplayName("[getById()] 활성 템플릿 ID -> 템플릿 반환")
		void getByIdSuccess() {
			// Arrange
			CouponTemplate template = activeTemplate(1L);
			given(couponTemplateQueryRepository.findById(1L)).willReturn(Optional.of(template));

			// Act
			CouponTemplate result = couponTemplateQueryService.getById(1L);

			// Assert
			assertThat(result.getId()).isEqualTo(1L);
		}


		@Test
		@DisplayName("[getById()] 존재하지 않는 ID -> COUPON_TEMPLATE_NOT_FOUND 예외")
		void getByIdNotFound() {
			// Arrange
			given(couponTemplateQueryRepository.findById(999L)).willReturn(Optional.empty());

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> couponTemplateQueryService.getById(999L));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.COUPON_TEMPLATE_NOT_FOUND),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.COUPON_TEMPLATE_NOT_FOUND.getMessage())
			);
		}


		@Test
		@DisplayName("[getById()] 삭제된 템플릿 ID -> COUPON_TEMPLATE_NOT_FOUND 예외")
		void getByIdDeleted() {
			// Arrange
			given(couponTemplateQueryRepository.findById(1L)).willReturn(Optional.of(deletedTemplate(1L)));

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> couponTemplateQueryService.getById(1L));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.COUPON_TEMPLATE_NOT_FOUND),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.COUPON_TEMPLATE_NOT_FOUND.getMessage())
			);
		}

	}


	@Nested
	@DisplayName("getByIdIncludeDeleted()")
	class GetByIdIncludeDeletedTest {

		@Test
		@DisplayName("[getByIdIncludeDeleted()] 활성 템플릿 -> 반환")
		void getByIdIncludeDeletedActive() {
			// Arrange
			given(couponTemplateQueryRepository.findById(1L)).willReturn(Optional.of(activeTemplate(1L)));

			// Act
			CouponTemplate result = couponTemplateQueryService.getByIdIncludeDeleted(1L);

			// Assert
			assertThat(result.getId()).isEqualTo(1L);
		}


		@Test
		@DisplayName("[getByIdIncludeDeleted()] 삭제된 템플릿 -> 삭제 여부 무관하게 반환")
		void getByIdIncludeDeletedDeleted() {
			// Arrange
			given(couponTemplateQueryRepository.findById(1L)).willReturn(Optional.of(deletedTemplate(1L)));

			// Act
			CouponTemplate result = couponTemplateQueryService.getByIdIncludeDeleted(1L);

			// Assert
			assertAll(
				() -> assertThat(result.getId()).isEqualTo(1L),
				() -> assertThat(result.isDeleted()).isTrue()
			);
		}


		@Test
		@DisplayName("[getByIdIncludeDeleted()] 존재하지 않는 ID -> COUPON_TEMPLATE_NOT_FOUND 예외")
		void getByIdIncludeDeletedNotFound() {
			// Arrange
			given(couponTemplateQueryRepository.findById(999L)).willReturn(Optional.empty());

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> couponTemplateQueryService.getByIdIncludeDeleted(999L));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.COUPON_TEMPLATE_NOT_FOUND),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.COUPON_TEMPLATE_NOT_FOUND.getMessage())
			);
		}

	}


	@Nested
	@DisplayName("getAll()")
	class GetAllTest {

		@Test
		@DisplayName("[getAll()] 쿠폰 템플릿 2건 -> AdminCouponTemplatePageOutDto 반환")
		void getAllSuccess() {
			// Arrange
			List<CouponTemplate> templates = List.of(activeTemplate(1L), activeTemplate(2L));
			PageResult<CouponTemplate> pageResult = new PageResult<>(templates, 0, 20, 2L);
			given(couponTemplateQueryRepository.findAll(any(PageCriteria.class))).willReturn(pageResult);

			// Act
			AdminCouponTemplatePageOutDto result = couponTemplateQueryService.getAll(0, 20);

			// Assert
			assertAll(
				() -> assertThat(result.content()).hasSize(2),
				() -> assertThat(result.totalElements()).isEqualTo(2L),
				() -> assertThat(result.page()).isEqualTo(0),
				() -> assertThat(result.size()).isEqualTo(20)
			);
		}


		@Test
		@DisplayName("[getAll()] 쿠폰 템플릿 없음 -> 빈 목록 반환")
		void getAllEmpty() {
			// Arrange
			PageResult<CouponTemplate> pageResult = new PageResult<>(List.of(), 0, 20, 0L);
			given(couponTemplateQueryRepository.findAll(any(PageCriteria.class))).willReturn(pageResult);

			// Act
			AdminCouponTemplatePageOutDto result = couponTemplateQueryService.getAll(0, 20);

			// Assert
			assertAll(
				() -> assertThat(result.content()).isEmpty(),
				() -> assertThat(result.totalElements()).isEqualTo(0L)
			);
		}

	}

}
