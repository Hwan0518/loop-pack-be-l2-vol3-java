package com.loopers.catalog.brand.application.service;


import com.loopers.catalog.brand.application.dto.out.BrandAdminPageOutDto;
import com.loopers.catalog.brand.domain.model.Brand;
import com.loopers.catalog.brand.domain.model.enums.VisibleStatus;
import com.loopers.catalog.brand.domain.model.vo.BrandDescription;
import com.loopers.catalog.brand.domain.model.vo.BrandName;
import com.loopers.catalog.brand.domain.repository.BrandQueryRepository;
import com.loopers.catalog.brand.domain.repository.vo.PageCriteria;
import com.loopers.catalog.brand.domain.repository.vo.PageResult;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
@DisplayName("BrandQueryService 테스트")
class BrandQueryServiceTest {

	@Mock
	private BrandQueryRepository brandQueryRepository;

	private BrandQueryService brandQueryService;


	@BeforeEach
	void setUp() {
		brandQueryService = new BrandQueryService(brandQueryRepository);
	}


	@Nested
	@DisplayName("getBrandById() 테스트")
	class GetBrandByIdTest {

		@Test
		@DisplayName("[BrandQueryService.getBrandById()] 존재하는 ID -> Brand 반환")
		void getBrandByIdSuccess() {
			// Arrange
			Brand brand = Brand.reconstruct(1L, BrandName.from("나이키"),
				BrandDescription.from("스포츠 브랜드"), VisibleStatus.HIDDEN, null);
			given(brandQueryRepository.findById(1L)).willReturn(Optional.of(brand));

			// Act
			Brand result = brandQueryService.getBrandById(1L);

			// Assert
			assertAll(
				() -> assertThat(result.getId()).isEqualTo(1L),
				() -> assertThat(result.getName().value()).isEqualTo("나이키")
			);
			verify(brandQueryRepository).findById(1L);
		}


		@Test
		@DisplayName("[BrandQueryService.getBrandById()] 존재하지 않는 ID -> BRAND_NOT_FOUND 예외")
		void getBrandByIdNotFound() {
			// Arrange
			given(brandQueryRepository.findById(999L)).willReturn(Optional.empty());

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> brandQueryService.getBrandById(999L));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.BRAND_NOT_FOUND),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.BRAND_NOT_FOUND.getMessage())
			);
			verify(brandQueryRepository).findById(999L);
		}

	}


	@Nested
	@DisplayName("getAdminBrandsAsPage() 테스트")
	class GetAdminBrandsAsPageTest {

		@Test
		@DisplayName("[BrandQueryService.getAdminBrandsAsPage()] visibleStatus=null -> 전체 브랜드 조회. BrandAdminPageOutDto 반환")
		void getAdminBrandsAsPageAll() {
			// Arrange
			PageCriteria expectedCriteria = new PageCriteria(0, 10);
			List<Brand> brands = List.of(
				Brand.reconstruct(1L, BrandName.from("나이키"),
					BrandDescription.from("스포츠 브랜드"), VisibleStatus.HIDDEN, null),
				Brand.reconstruct(2L, BrandName.from("아디다스"),
					BrandDescription.from("독일 스포츠 브랜드"), VisibleStatus.VISIBLE, null)
			);
			PageResult<Brand> pageResult = new PageResult<>(brands, 0, 10, 2);
			given(brandQueryRepository.findAll(expectedCriteria)).willReturn(pageResult);

			// Act
			BrandAdminPageOutDto result = brandQueryService.getAdminBrandsAsPage(null, 0, 10);

			// Assert
			assertAll(
				() -> assertThat(result.content()).hasSize(2),
				() -> assertThat(result.content().get(0).visibleStatus()).isEqualTo(VisibleStatus.HIDDEN),
				() -> assertThat(result.content().get(1).visibleStatus()).isEqualTo(VisibleStatus.VISIBLE),
				() -> assertThat(result.totalElements()).isEqualTo(2)
			);
			verify(brandQueryRepository).findAll(expectedCriteria);
		}


		@Test
		@DisplayName("[BrandQueryService.getAdminBrandsAsPage()] visibleStatus=VISIBLE -> VISIBLE 브랜드만 조회")
		void getAdminBrandsAsPageVisibleOnly() {
			// Arrange
			PageCriteria expectedCriteria = new PageCriteria(0, 10);
			List<Brand> brands = List.of(
				Brand.reconstruct(2L, BrandName.from("아디다스"),
					BrandDescription.from("독일 스포츠 브랜드"), VisibleStatus.VISIBLE, null)
			);
			PageResult<Brand> pageResult = new PageResult<>(brands, 0, 10, 1);
			given(brandQueryRepository.findAllByVisibleStatus(VisibleStatus.VISIBLE, expectedCriteria))
				.willReturn(pageResult);

			// Act
			BrandAdminPageOutDto result = brandQueryService.getAdminBrandsAsPage(VisibleStatus.VISIBLE, 0, 10);

			// Assert
			assertAll(
				() -> assertThat(result.content()).hasSize(1),
				() -> assertThat(result.content().get(0).visibleStatus()).isEqualTo(VisibleStatus.VISIBLE),
				() -> assertThat(result.totalElements()).isEqualTo(1)
			);
			verify(brandQueryRepository).findAllByVisibleStatus(VisibleStatus.VISIBLE, expectedCriteria);
		}


		@Test
		@DisplayName("[BrandQueryService.getAdminBrandsAsPage()] visibleStatus=HIDDEN -> HIDDEN 브랜드만 조회")
		void getAdminBrandsAsPageHiddenOnly() {
			// Arrange
			PageCriteria expectedCriteria = new PageCriteria(0, 10);
			List<Brand> brands = List.of(
				Brand.reconstruct(1L, BrandName.from("나이키"),
					BrandDescription.from("스포츠 브랜드"), VisibleStatus.HIDDEN, null)
			);
			PageResult<Brand> pageResult = new PageResult<>(brands, 0, 10, 1);
			given(brandQueryRepository.findAllByVisibleStatus(VisibleStatus.HIDDEN, expectedCriteria))
				.willReturn(pageResult);

			// Act
			BrandAdminPageOutDto result = brandQueryService.getAdminBrandsAsPage(VisibleStatus.HIDDEN, 0, 10);

			// Assert
			assertAll(
				() -> assertThat(result.content()).hasSize(1),
				() -> assertThat(result.content().get(0).visibleStatus()).isEqualTo(VisibleStatus.HIDDEN),
				() -> assertThat(result.totalElements()).isEqualTo(1)
			);
			verify(brandQueryRepository).findAllByVisibleStatus(VisibleStatus.HIDDEN, expectedCriteria);
		}

	}

}
