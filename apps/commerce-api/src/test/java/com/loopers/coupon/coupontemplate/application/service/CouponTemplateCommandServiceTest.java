package com.loopers.coupon.coupontemplate.application.service;


import com.loopers.coupon.coupontemplate.domain.model.CouponTemplate;
import com.loopers.coupon.coupontemplate.domain.model.enums.CouponType;
import com.loopers.coupon.coupontemplate.domain.repository.CouponTemplateCommandRepository;
import com.loopers.coupon.coupontemplate.domain.repository.CouponTemplateQueryRepository;
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
@DisplayName("CouponTemplateCommandService 단위 테스트")
class CouponTemplateCommandServiceTest {

	@Mock
	private CouponTemplateCommandRepository couponTemplateCommandRepository;
	@Mock
	private CouponTemplateQueryRepository couponTemplateQueryRepository;

	private CouponTemplateCommandService couponTemplateCommandService;

	private final LocalDateTime futureExpiredAt = LocalDateTime.now().plusDays(30);


	@BeforeEach
	void setUp() {
		couponTemplateCommandService = new CouponTemplateCommandService(
			couponTemplateCommandRepository, couponTemplateQueryRepository
		);
	}


	// 테스트 헬퍼 — 활성 템플릿
	private CouponTemplate activeTemplate(Long id) {
		return CouponTemplate.reconstruct(
			id, "테스트 쿠폰", CouponType.FIXED, new BigDecimal("5000"),
			null, null, futureExpiredAt, null
		);
	}

	// 테스트 헬퍼 — 소프트 삭제된 템플릿
	private CouponTemplate deletedTemplate(Long id) {
		return CouponTemplate.reconstruct(
			id, "삭제된 쿠폰", CouponType.FIXED, new BigDecimal("5000"),
			null, null, futureExpiredAt, ZonedDateTime.now()
		);
	}


	@Nested
	@DisplayName("save()")
	class SaveTest {

		@Test
		@DisplayName("[save()] 유효한 CouponTemplate -> 저장 후 반환")
		void saveSuccess() {
			// Arrange
			CouponTemplate template = CouponTemplate.create(
				"신규 쿠폰", CouponType.FIXED, new BigDecimal("5000"), null, null, futureExpiredAt
			);
			CouponTemplate saved = CouponTemplate.reconstruct(
				1L, "신규 쿠폰", CouponType.FIXED, new BigDecimal("5000"),
				null, null, futureExpiredAt, null
			);
			given(couponTemplateCommandRepository.save(any(CouponTemplate.class))).willReturn(saved);

			// Act
			CouponTemplate result = couponTemplateCommandService.save(template);

			// Assert
			assertAll(
				() -> assertThat(result.getId()).isEqualTo(1L),
				() -> verify(couponTemplateCommandRepository).save(template)
			);
		}

	}


	@Nested
	@DisplayName("update()")
	class UpdateTest {

		@Test
		@DisplayName("[update()] 유효한 수정 요청 -> name, expiredAt 변경 후 저장")
		void updateSuccess() {
			// Arrange
			CouponTemplate template = activeTemplate(1L);
			given(couponTemplateQueryRepository.findById(1L)).willReturn(Optional.of(template));
			given(couponTemplateCommandRepository.save(any(CouponTemplate.class))).willAnswer(invocation -> invocation.getArgument(0));
			LocalDateTime newExpiredAt = LocalDateTime.now().plusDays(60);

			// Act
			CouponTemplate result = couponTemplateCommandService.update(1L, "새 이름", newExpiredAt);

			// Assert
			assertAll(
				() -> assertThat(result.getName()).isEqualTo("새 이름"),
				() -> assertThat(result.getExpiredAt()).isEqualTo(newExpiredAt),
				() -> verify(couponTemplateCommandRepository).save(template)
			);
		}


		@Test
		@DisplayName("[update()] name null -> 이름 변경 없이 나머지만 수정")
		void updateWithNullName() {
			// Arrange
			CouponTemplate template = activeTemplate(1L);
			given(couponTemplateQueryRepository.findById(1L)).willReturn(Optional.of(template));
			given(couponTemplateCommandRepository.save(any(CouponTemplate.class))).willAnswer(invocation -> invocation.getArgument(0));

			// Act
			CouponTemplate result = couponTemplateCommandService.update(1L, null, null);

			// Assert
			assertThat(result.getName()).isEqualTo("테스트 쿠폰");
		}


		@Test
		@DisplayName("[update()] 존재하지 않는 ID -> COUPON_TEMPLATE_NOT_FOUND 예외")
		void updateNotFound() {
			// Arrange
			given(couponTemplateQueryRepository.findById(999L)).willReturn(Optional.empty());

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> couponTemplateCommandService.update(999L, "이름", null));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.COUPON_TEMPLATE_NOT_FOUND),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.COUPON_TEMPLATE_NOT_FOUND.getMessage())
			);
		}


		@Test
		@DisplayName("[update()] 삭제된 템플릿 수정 시도 -> COUPON_TEMPLATE_NOT_FOUND 예외")
		void updateDeletedTemplate() {
			// Arrange
			given(couponTemplateQueryRepository.findById(1L)).willReturn(Optional.of(deletedTemplate(1L)));

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> couponTemplateCommandService.update(1L, "이름", null));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.COUPON_TEMPLATE_NOT_FOUND),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.COUPON_TEMPLATE_NOT_FOUND.getMessage())
			);
		}

	}


	@Nested
	@DisplayName("delete()")
	class DeleteTest {

		@Test
		@DisplayName("[delete()] 활성 템플릿 -> soft delete 후 저장")
		void deleteSuccess() {
			// Arrange
			CouponTemplate template = activeTemplate(1L);
			given(couponTemplateQueryRepository.findById(1L)).willReturn(Optional.of(template));
			given(couponTemplateCommandRepository.save(any(CouponTemplate.class))).willAnswer(invocation -> invocation.getArgument(0));

			// Act
			couponTemplateCommandService.delete(1L);

			// Assert
			assertAll(
				() -> assertThat(template.isDeleted()).isTrue(),
				() -> verify(couponTemplateCommandRepository).save(template)
			);
		}


		@Test
		@DisplayName("[delete()] 존재하지 않는 ID -> COUPON_TEMPLATE_NOT_FOUND 예외")
		void deleteNotFound() {
			// Arrange
			given(couponTemplateQueryRepository.findById(999L)).willReturn(Optional.empty());

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> couponTemplateCommandService.delete(999L));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.COUPON_TEMPLATE_NOT_FOUND),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.COUPON_TEMPLATE_NOT_FOUND.getMessage())
			);
		}


		@Test
		@DisplayName("[delete()] 이미 삭제된 템플릿 -> COUPON_TEMPLATE_NOT_FOUND 예외")
		void deleteAlreadyDeleted() {
			// Arrange
			given(couponTemplateQueryRepository.findById(1L)).willReturn(Optional.of(deletedTemplate(1L)));

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> couponTemplateCommandService.delete(1L));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.COUPON_TEMPLATE_NOT_FOUND),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.COUPON_TEMPLATE_NOT_FOUND.getMessage())
			);
		}

	}

}
