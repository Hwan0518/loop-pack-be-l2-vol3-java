package com.loopers.catalog.brand.application.service;


import com.loopers.catalog.brand.application.dto.in.AdminBrandCreateInDto;
import com.loopers.catalog.brand.application.dto.in.AdminBrandUpdateInDto;
import com.loopers.catalog.brand.application.dto.in.AdminBrandVisibleStatusUpdateInDto;
import com.loopers.catalog.brand.domain.event.BrandDeletedEvent;
import com.loopers.catalog.brand.domain.model.Brand;
import com.loopers.catalog.brand.domain.model.enums.VisibleStatus;
import com.loopers.catalog.brand.domain.model.vo.BrandDescription;
import com.loopers.catalog.brand.domain.model.vo.BrandName;
import com.loopers.catalog.brand.domain.repository.BrandCommandRepository;
import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
@DisplayName("BrandCommandService 테스트")
class BrandCommandServiceTest {

	@Mock
	private BrandCommandRepository brandCommandRepository;

	@Mock
	private ApplicationEventPublisher eventPublisher;

	private BrandCommandService brandCommandService;


	@BeforeEach
	void setUp() {
		brandCommandService = new BrandCommandService(
			brandCommandRepository, eventPublisher
		);
	}


	@Nested
	@DisplayName("createBrand() 테스트")
	class CreateBrandTest {

		@Test
		@DisplayName("[BrandCommandService.createBrand()] 유효한 입력 -> Brand 생성 후 저장. ID 할당, visibleStatus=HIDDEN")
		void createBrandSuccess() {
			// Arrange
			AdminBrandCreateInDto inDto = new AdminBrandCreateInDto("나이키", "스포츠 브랜드");
			given(brandCommandRepository.save(any(Brand.class))).willAnswer(invocation -> {
				Brand saved = invocation.getArgument(0);
				return Brand.reconstruct(1L, saved.getName(), saved.getDescription(),
					saved.getVisibleStatus(), null);
			});

			// Act
			Brand result = brandCommandService.createBrand(inDto);

			// Assert
			assertAll(
				() -> assertThat(result.getId()).isEqualTo(1L),
				() -> assertThat(result.getName().value()).isEqualTo("나이키"),
				() -> assertThat(result.getDescription().value()).isEqualTo("스포츠 브랜드"),
				() -> assertThat(result.getVisibleStatus()).isEqualTo(VisibleStatus.HIDDEN)
			);
			verify(brandCommandRepository).save(argThat(brand ->
				brand.getName().value().equals("나이키") && brand.getDescription().value().equals("스포츠 브랜드")
			));
		}


		@Test
		@DisplayName("[BrandCommandService.createBrand()] description이 null -> Brand 생성. description=null")
		void createBrandWithNullDescription() {
			// Arrange
			AdminBrandCreateInDto inDto = new AdminBrandCreateInDto("나이키", null);
			given(brandCommandRepository.save(any(Brand.class))).willAnswer(invocation -> {
				Brand saved = invocation.getArgument(0);
				return Brand.reconstruct(1L, saved.getName(), saved.getDescription(),
					saved.getVisibleStatus(), null);
			});

			// Act
			Brand result = brandCommandService.createBrand(inDto);

			// Assert
			assertAll(
				() -> assertThat(result.getId()).isEqualTo(1L),
				() -> assertThat(result.getDescription()).isNull()
			);
		}

	}


	@Nested
	@DisplayName("updateBrand() 테스트")
	class UpdateBrandTest {

		@Test
		@DisplayName("[BrandCommandService.updateBrand()] 유효한 입력 -> 브랜드명/설명 변경 후 저장")
		void updateBrandSuccess() {
			// Arrange
			Brand brand = Brand.reconstruct(1L, BrandName.from("나이키"),
				BrandDescription.from("스포츠 브랜드"), VisibleStatus.HIDDEN, null);
			AdminBrandUpdateInDto inDto = new AdminBrandUpdateInDto("아디다스", "독일 스포츠 브랜드", null);
			given(brandCommandRepository.save(any(Brand.class))).willAnswer(invocation ->
				invocation.getArgument(0)
			);

			// Act
			Brand result = brandCommandService.updateBrand(brand, inDto);

			// Assert
			assertAll(
				() -> assertThat(result.getName().value()).isEqualTo("아디다스"),
				() -> assertThat(result.getDescription().value()).isEqualTo("독일 스포츠 브랜드"),
				() -> assertThat(result.getVisibleStatus()).isEqualTo(VisibleStatus.HIDDEN)
			);
			verify(brandCommandRepository).save(brand);
		}


		@Test
		@DisplayName("[BrandCommandService.updateBrand()] visibleStatus 포함 -> 노출 상태도 변경")
		void updateBrandWithVisibleStatus() {
			// Arrange
			Brand brand = Brand.reconstruct(1L, BrandName.from("나이키"),
				BrandDescription.from("스포츠 브랜드"), VisibleStatus.HIDDEN, null);
			AdminBrandUpdateInDto inDto = new AdminBrandUpdateInDto("나이키", "스포츠 브랜드", VisibleStatus.VISIBLE);
			given(brandCommandRepository.save(any(Brand.class))).willAnswer(invocation ->
				invocation.getArgument(0)
			);

			// Act
			Brand result = brandCommandService.updateBrand(brand, inDto);

			// Assert
			assertThat(result.getVisibleStatus()).isEqualTo(VisibleStatus.VISIBLE);
			verify(brandCommandRepository).save(brand);
		}

	}


	@Nested
	@DisplayName("deleteBrand() 테스트")
	class DeleteBrandTest {

		@Test
		@DisplayName("[BrandCommandService.deleteBrand()] 활성 상품 없음 -> 검증 통과 후 브랜드 삭제 및 이벤트 발행")
		void deleteBrandSuccess() {
			// Arrange
			Brand brand = Brand.reconstruct(1L, BrandName.from("나이키"),
				BrandDescription.from("스포츠 브랜드"), VisibleStatus.HIDDEN, null);
			willDoNothing().given(brandCommandRepository).delete(brand);

			// Act
			assertDoesNotThrow(() -> brandCommandService.deleteBrand(brand, false));

			// Assert
			verify(brandCommandRepository).delete(brand);
			verify(eventPublisher).publishEvent(argThat((BrandDeletedEvent event) ->
				event.brandId().equals(1L)
			));
		}


		@Test
		@DisplayName("[BrandCommandService.deleteBrand()] 활성 상품 존재 -> BRAND_HAS_ACTIVE_PRODUCTS 예외. 삭제/이벤트 미발생")
		void deleteBrandFailWhenHasActiveProducts() {
			// Arrange
			Brand brand = Brand.reconstruct(1L, BrandName.from("나이키"),
				BrandDescription.from("스포츠 브랜드"), VisibleStatus.HIDDEN, null);

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> brandCommandService.deleteBrand(brand, true));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.BRAND_HAS_ACTIVE_PRODUCTS),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.BRAND_HAS_ACTIVE_PRODUCTS.getMessage())
			);
			verify(brandCommandRepository, never()).delete(any(Brand.class));
			verify(eventPublisher, never()).publishEvent(any(BrandDeletedEvent.class));
		}

	}


	@Nested
	@DisplayName("updateVisibleStatus() 테스트")
	class UpdateVisibleStatusTest {

		@Test
		@DisplayName("[BrandCommandService.updateVisibleStatus()] VISIBLE -> 노출 상태 변경 후 저장")
		void updateVisibleStatusSuccess() {
			// Arrange
			Brand brand = Brand.reconstruct(1L, BrandName.from("나이키"),
				BrandDescription.from("스포츠 브랜드"), VisibleStatus.HIDDEN, null);
			AdminBrandVisibleStatusUpdateInDto inDto = new AdminBrandVisibleStatusUpdateInDto(VisibleStatus.VISIBLE);
			given(brandCommandRepository.save(any(Brand.class))).willAnswer(invocation ->
				invocation.getArgument(0)
			);

			// Act
			Brand result = brandCommandService.updateVisibleStatus(brand, inDto);

			// Assert
			assertThat(result.getVisibleStatus()).isEqualTo(VisibleStatus.VISIBLE);
			verify(brandCommandRepository).save(brand);
		}


		@Test
		@DisplayName("[BrandCommandService.updateVisibleStatus()] null -> INVALID_BRAND_VISIBLE_STATUS 예외")
		void updateVisibleStatusFailWhenNull() {
			// Arrange
			Brand brand = Brand.reconstruct(1L, BrandName.from("나이키"),
				BrandDescription.from("스포츠 브랜드"), VisibleStatus.HIDDEN, null);
			AdminBrandVisibleStatusUpdateInDto inDto = new AdminBrandVisibleStatusUpdateInDto(null);

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> brandCommandService.updateVisibleStatus(brand, inDto));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_BRAND_VISIBLE_STATUS),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_BRAND_VISIBLE_STATUS.getMessage())
			);
			verify(brandCommandRepository, never()).save(any(Brand.class));
		}

	}

}
