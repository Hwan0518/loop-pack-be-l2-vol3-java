package com.loopers.catalog.brand.application.facade;


import com.loopers.catalog.brand.application.dto.in.AdminBrandCreateInDto;
import com.loopers.catalog.brand.application.dto.in.AdminBrandUpdateInDto;
import com.loopers.catalog.brand.application.dto.in.AdminBrandVisibleStatusUpdateInDto;
import com.loopers.catalog.brand.application.dto.out.AdminBrandDetailOutDto;
import com.loopers.catalog.brand.application.service.BrandCleanupCommandService;
import com.loopers.catalog.brand.application.service.BrandCommandService;
import com.loopers.catalog.brand.application.service.BrandQueryService;
import com.loopers.catalog.brand.domain.model.Brand;
import com.loopers.catalog.brand.domain.model.enums.VisibleStatus;
import com.loopers.catalog.brand.domain.model.vo.BrandDescription;
import com.loopers.catalog.brand.domain.model.vo.BrandName;
import com.loopers.catalog.product.application.service.ProductQueryService;
import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
@DisplayName("BrandCommandFacade 테스트")
class BrandCommandFacadeTest {

	@Mock
	private BrandCommandService brandCommandService;

	@Mock
	private BrandCleanupCommandService brandCleanupCommandService;

	@Mock
	private BrandQueryService brandQueryService;

	@Mock
	private ProductQueryService productQueryService;

	private BrandCommandFacade brandCommandFacade;


	@BeforeEach
	void setUp() {
		brandCommandFacade = new BrandCommandFacade(
			brandCommandService, brandCleanupCommandService,
			brandQueryService, productQueryService
		);
	}


	@Nested
	@DisplayName("createBrand() 테스트")
	class CreateBrandTest {

		@Test
		@DisplayName("[BrandCommandFacade.createBrand()] 유효한 입력 -> AdminBrandDetailOutDto 반환. visibleStatus=HIDDEN")
		void createBrandSuccess() {
			// Arrange
			AdminBrandCreateInDto inDto = new AdminBrandCreateInDto("나이키", "스포츠 브랜드");
			Brand savedBrand = Brand.reconstruct(1L, BrandName.from("나이키"),
				BrandDescription.from("스포츠 브랜드"), VisibleStatus.HIDDEN, null);
			given(brandCommandService.createBrand(inDto)).willReturn(savedBrand);

			// Act
			AdminBrandDetailOutDto result = brandCommandFacade.createBrand(inDto);

			// Assert
			assertAll(
				() -> assertThat(result.id()).isEqualTo(1L),
				() -> assertThat(result.name()).isEqualTo("나이키"),
				() -> assertThat(result.description()).isEqualTo("스포츠 브랜드"),
				() -> assertThat(result.visibleStatus()).isEqualTo(VisibleStatus.HIDDEN)
			);
			verify(brandCommandService).createBrand(inDto);
		}

	}


	@Nested
	@DisplayName("updateBrand() 테스트")
	class UpdateBrandTest {

		@Test
		@DisplayName("[BrandCommandFacade.updateBrand()] 유효한 입력 -> 조회 후 수정. AdminBrandDetailOutDto 반환")
		void updateBrandSuccess() {
			// Arrange
			Brand brand = Brand.reconstruct(1L, BrandName.from("나이키"),
				BrandDescription.from("스포츠 브랜드"), VisibleStatus.HIDDEN, null);
			AdminBrandUpdateInDto inDto = new AdminBrandUpdateInDto("아디다스", "독일 스포츠 브랜드", null);
			Brand updatedBrand = Brand.reconstruct(1L, BrandName.from("아디다스"),
				BrandDescription.from("독일 스포츠 브랜드"), VisibleStatus.HIDDEN, null);

			given(brandQueryService.getBrandById(1L)).willReturn(brand);
			given(brandCommandService.updateBrand(brand, inDto)).willReturn(updatedBrand);

			// Act
			AdminBrandDetailOutDto result = brandCommandFacade.updateBrand(1L, inDto);

			// Assert
			assertAll(
				() -> assertThat(result.id()).isEqualTo(1L),
				() -> assertThat(result.name()).isEqualTo("아디다스"),
				() -> assertThat(result.description()).isEqualTo("독일 스포츠 브랜드"),
				() -> assertThat(result.visibleStatus()).isEqualTo(VisibleStatus.HIDDEN)
			);
			verify(brandQueryService).getBrandById(1L);
			verify(brandCommandService).updateBrand(brand, inDto);
		}


		@Test
		@DisplayName("[BrandCommandFacade.updateBrand()] 존재하지 않는 ID -> BRAND_NOT_FOUND 예외 전파")
		void updateBrandNotFound() {
			// Arrange
			AdminBrandUpdateInDto inDto = new AdminBrandUpdateInDto("아디다스", "독일 스포츠 브랜드", null);
			given(brandQueryService.getBrandById(999L))
				.willThrow(new CoreException(ErrorType.BRAND_NOT_FOUND));

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> brandCommandFacade.updateBrand(999L, inDto));

			// Assert
			assertThat(exception.getErrorType()).isEqualTo(ErrorType.BRAND_NOT_FOUND);
			verify(brandCommandService, never()).updateBrand(any(), any());
		}

	}


	@Nested
	@DisplayName("deleteBrand() 테스트")
	class DeleteBrandTest {

		@Test
		@DisplayName("[BrandCommandFacade.deleteBrand()] 활성 상품 없음 -> 조회 후 삭제 및 좋아요 정리 오케스트레이션 수행")
		void deleteBrandSuccess() {
			// Arrange
			Brand brand = Brand.reconstruct(1L, BrandName.from("나이키"),
				BrandDescription.from("스포츠 브랜드"), VisibleStatus.HIDDEN, null);
			given(brandQueryService.getBrandById(1L)).willReturn(brand);
			given(productQueryService.existsActiveByBrandId(1L)).willReturn(false);
			willDoNothing().given(brandCommandService).deleteBrand(brand, false);
			willDoNothing().given(brandCleanupCommandService).deleteAllBrandLikes(1L);

			// Act
			assertDoesNotThrow(() -> brandCommandFacade.deleteBrand(1L));

			// Assert
			verify(brandQueryService).getBrandById(1L);
			verify(productQueryService).existsActiveByBrandId(1L);
			verify(brandCommandService).deleteBrand(brand, false);
			verify(brandCleanupCommandService).deleteAllBrandLikes(1L);
		}


		@Test
		@DisplayName("[BrandCommandFacade.deleteBrand()] 활성 상품 존재 -> BRAND_HAS_ACTIVE_PRODUCTS 예외. 정리 서비스 미호출")
		void deleteBrandWithActiveProducts() {
			// Arrange
			Brand brand = Brand.reconstruct(1L, BrandName.from("나이키"),
				BrandDescription.from("스포츠 브랜드"), VisibleStatus.HIDDEN, null);
			given(brandQueryService.getBrandById(1L)).willReturn(brand);
			given(productQueryService.existsActiveByBrandId(1L)).willReturn(true);
			willThrow(new CoreException(ErrorType.BRAND_HAS_ACTIVE_PRODUCTS))
				.given(brandCommandService).deleteBrand(brand, true);

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> brandCommandFacade.deleteBrand(1L));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.BRAND_HAS_ACTIVE_PRODUCTS),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.BRAND_HAS_ACTIVE_PRODUCTS.getMessage())
			);
			verify(brandCommandService).deleteBrand(brand, true);
			verify(brandCleanupCommandService, never()).deleteAllBrandLikes(any());
		}


		@Test
		@DisplayName("[BrandCommandFacade.deleteBrand()] 존재하지 않는 ID -> BRAND_NOT_FOUND 예외 전파. commandService/cleanupService 미호출")
		void deleteBrandNotFound() {
			// Arrange
			given(brandQueryService.getBrandById(999L))
				.willThrow(new CoreException(ErrorType.BRAND_NOT_FOUND));

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> brandCommandFacade.deleteBrand(999L));

			// Assert
			assertThat(exception.getErrorType()).isEqualTo(ErrorType.BRAND_NOT_FOUND);
			verify(productQueryService, never()).existsActiveByBrandId(any());
			verify(brandCommandService, never()).deleteBrand(any(), eq(false));
			verify(brandCommandService, never()).deleteBrand(any(), eq(true));
			verify(brandCleanupCommandService, never()).deleteAllBrandLikes(any());
		}

	}


	@Nested
	@DisplayName("updateVisibleStatus() 테스트")
	class UpdateVisibleStatusTest {

		@Test
		@DisplayName("[BrandCommandFacade.updateVisibleStatus()] VISIBLE -> 조회 후 노출 상태 변경. AdminBrandDetailOutDto 반환")
		void updateVisibleStatusSuccess() {
			// Arrange
			Brand brand = Brand.reconstruct(1L, BrandName.from("나이키"),
				BrandDescription.from("스포츠 브랜드"), VisibleStatus.HIDDEN, null);
			AdminBrandVisibleStatusUpdateInDto inDto = new AdminBrandVisibleStatusUpdateInDto(VisibleStatus.VISIBLE);
			Brand updatedBrand = Brand.reconstruct(1L, BrandName.from("나이키"),
				BrandDescription.from("스포츠 브랜드"), VisibleStatus.VISIBLE, null);

			given(brandQueryService.getBrandById(1L)).willReturn(brand);
			given(brandCommandService.updateVisibleStatus(brand, inDto)).willReturn(updatedBrand);

			// Act
			AdminBrandDetailOutDto result = brandCommandFacade.updateVisibleStatus(1L, inDto);

			// Assert
			assertAll(
				() -> assertThat(result.id()).isEqualTo(1L),
				() -> assertThat(result.visibleStatus()).isEqualTo(VisibleStatus.VISIBLE)
			);
			verify(brandQueryService).getBrandById(1L);
			verify(brandCommandService).updateVisibleStatus(brand, inDto);
		}


		@Test
		@DisplayName("[BrandCommandFacade.updateVisibleStatus()] 존재하지 않는 ID -> BRAND_NOT_FOUND 예외 전파")
		void updateVisibleStatusNotFound() {
			// Arrange
			AdminBrandVisibleStatusUpdateInDto inDto = new AdminBrandVisibleStatusUpdateInDto(VisibleStatus.VISIBLE);
			given(brandQueryService.getBrandById(999L))
				.willThrow(new CoreException(ErrorType.BRAND_NOT_FOUND));

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> brandCommandFacade.updateVisibleStatus(999L, inDto));

			// Assert
			assertThat(exception.getErrorType()).isEqualTo(ErrorType.BRAND_NOT_FOUND);
			verify(brandCommandService, never()).updateVisibleStatus(any(), any());
		}

	}

}
