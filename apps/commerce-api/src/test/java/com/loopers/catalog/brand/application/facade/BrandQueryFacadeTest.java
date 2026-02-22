package com.loopers.catalog.brand.application.facade;


import com.loopers.catalog.brand.application.dto.out.BrandAdminDetailOutDto;
import com.loopers.catalog.brand.application.dto.out.BrandAdminOutDto;
import com.loopers.catalog.brand.application.dto.out.BrandAdminPageOutDto;
import com.loopers.catalog.brand.application.service.BrandQueryService;
import com.loopers.catalog.brand.domain.model.Brand;
import com.loopers.catalog.brand.domain.model.enums.VisibleStatus;
import com.loopers.catalog.brand.domain.model.vo.BrandDescription;
import com.loopers.catalog.brand.domain.model.vo.BrandName;
import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
@DisplayName("BrandQueryFacade 테스트")
class BrandQueryFacadeTest {

	@Mock
	private BrandQueryService brandQueryService;

	private BrandQueryFacade brandQueryFacade;


	@BeforeEach
	void setUp() {
		brandQueryFacade = new BrandQueryFacade(brandQueryService);
	}


	@Nested
	@DisplayName("getAdminBrands() 테스트")
	class GetAdminBrandsTest {

		@Test
		@DisplayName("[BrandQueryFacade.getAdminBrands()] visibleStatus=null -> 전체 조회. BrandAdminPageOutDto 반환")
		void getAdminBrandsAll() {
			// Arrange
			List<BrandAdminOutDto> content = List.of(
				new BrandAdminOutDto(1L, "나이키", VisibleStatus.HIDDEN),
				new BrandAdminOutDto(2L, "아디다스", VisibleStatus.VISIBLE)
			);
			BrandAdminPageOutDto pageOutDto = new BrandAdminPageOutDto(content, 0, 10, 2);
			given(brandQueryService.getAdminBrandsAsPage(null, 0, 10)).willReturn(pageOutDto);

			// Act
			BrandAdminPageOutDto result = brandQueryFacade.getAdminBrands(null, 0, 10);

			// Assert
			assertAll(
				() -> assertThat(result.content()).hasSize(2),
				() -> assertThat(result.totalElements()).isEqualTo(2)
			);
			verify(brandQueryService).getAdminBrandsAsPage(null, 0, 10);
		}


		@Test
		@DisplayName("[BrandQueryFacade.getAdminBrands()] visibleStatus=VISIBLE -> VISIBLE 필터 조회")
		void getAdminBrandsVisible() {
			// Arrange
			List<BrandAdminOutDto> content = List.of(
				new BrandAdminOutDto(2L, "아디다스", VisibleStatus.VISIBLE)
			);
			BrandAdminPageOutDto pageOutDto = new BrandAdminPageOutDto(content, 0, 10, 1);
			given(brandQueryService.getAdminBrandsAsPage(VisibleStatus.VISIBLE, 0, 10)).willReturn(pageOutDto);

			// Act
			BrandAdminPageOutDto result = brandQueryFacade.getAdminBrands(VisibleStatus.VISIBLE, 0, 10);

			// Assert
			assertAll(
				() -> assertThat(result.content()).hasSize(1),
				() -> assertThat(result.content().get(0).visibleStatus()).isEqualTo(VisibleStatus.VISIBLE)
			);
			verify(brandQueryService).getAdminBrandsAsPage(VisibleStatus.VISIBLE, 0, 10);
		}

	}


	@Nested
	@DisplayName("getAdminBrand() 테스트")
	class GetAdminBrandTest {

		@Test
		@DisplayName("[BrandQueryFacade.getAdminBrand()] HIDDEN 브랜드도 조회 가능 -> BrandAdminDetailOutDto 반환")
		void getAdminBrandSuccess() {
			// Arrange
			Brand brand = Brand.reconstruct(1L, BrandName.from("나이키"),
				BrandDescription.from("스포츠 브랜드"), VisibleStatus.HIDDEN, null);
			given(brandQueryService.getBrandById(1L)).willReturn(brand);

			// Act
			BrandAdminDetailOutDto result = brandQueryFacade.getAdminBrand(1L);

			// Assert
			assertAll(
				() -> assertThat(result.id()).isEqualTo(1L),
				() -> assertThat(result.name()).isEqualTo("나이키"),
				() -> assertThat(result.description()).isEqualTo("스포츠 브랜드"),
				() -> assertThat(result.visibleStatus()).isEqualTo(VisibleStatus.HIDDEN)
			);
			verify(brandQueryService).getBrandById(1L);
		}


		@Test
		@DisplayName("[BrandQueryFacade.getAdminBrand()] 존재하지 않는 ID -> BRAND_NOT_FOUND 예외 전파")
		void getAdminBrandNotFound() {
			// Arrange
			given(brandQueryService.getBrandById(999L))
				.willThrow(new CoreException(ErrorType.BRAND_NOT_FOUND));

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> brandQueryFacade.getAdminBrand(999L));

			// Assert
			assertThat(exception.getErrorType()).isEqualTo(ErrorType.BRAND_NOT_FOUND);
		}

	}

}
