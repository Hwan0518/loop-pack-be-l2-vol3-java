package com.loopers.catalog.product.application.facade;


import com.loopers.catalog.brand.application.service.BrandQueryService;
import com.loopers.catalog.brand.domain.model.Brand;
import com.loopers.catalog.brand.domain.model.vo.BrandName;
import com.loopers.catalog.product.application.dto.out.*;
import com.loopers.catalog.product.application.service.ProductQueryService;
import com.loopers.catalog.product.domain.model.Product;
import com.loopers.catalog.product.domain.model.enums.ProductSortType;
import com.loopers.catalog.product.domain.model.vo.Money;
import com.loopers.catalog.product.domain.model.vo.ProductName;
import com.loopers.catalog.product.domain.model.vo.Stock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
@DisplayName("ProductQueryFacade 단위 테스트")
class ProductQueryFacadeTest {

	@Mock
	private ProductQueryService productQueryService;
	@Mock
	private BrandQueryService brandQueryService;

	private ProductQueryFacade productQueryFacade;


	@BeforeEach
	void setUp() {
		productQueryFacade = new ProductQueryFacade(
			productQueryService, brandQueryService
		);
	}


	private Product createTestProduct() {
		return Product.reconstruct(1L, 1L,
			ProductName.from("테스트 상품"),
			Money.from(new BigDecimal("10000")),
			Stock.from(100L),
			null, 0L, 0L, null);
	}


	private Brand createTestBrand() {
		return Brand.reconstruct(1L, BrandName.from("나이키"), null, null, null);
	}


	@Nested
	@DisplayName("getProduct()")
	class GetProductTest {

		@Test
		@DisplayName("[getProduct()] 활성 상품 조회 -> ProductDetailOutDto 반환. 브랜드명 포함")
		void getProductSuccess() {
			// Arrange
			Product product = createTestProduct();
			Brand brand = createTestBrand();
			given(productQueryService.findActiveById(1L)).willReturn(product);
			given(brandQueryService.getBrandById(1L)).willReturn(brand);

			// Act
			ProductDetailOutDto result = productQueryFacade.getProduct(1L);

			// Assert
			assertAll(
				() -> assertThat(result.id()).isEqualTo(1L),
				() -> assertThat(result.brandName()).isEqualTo("나이키"),
				() -> assertThat(result.name()).isEqualTo("테스트 상품"),
				() -> verify(productQueryService).findActiveById(1L),
				() -> verify(brandQueryService).getBrandById(1L)
			);
		}

	}


	@Nested
	@DisplayName("getProducts()")
	class GetProductsTest {

		@Test
		@DisplayName("[getProducts()] 검색 조건으로 조회 -> ProductPageOutDto 반환")
		void getProductsSuccess() {
			// Arrange
			ProductOutDto outDto = new ProductOutDto(1L, 1L, null, "상품", new BigDecimal("10000"), 100L, 0L);
			ProductPageOutDto pageOutDto = new ProductPageOutDto(List.of(outDto), 0, 20, 1);
			given(productQueryService.searchProducts(null, ProductSortType.LATEST, 0, 20)).willReturn(pageOutDto);

			// Act
			ProductPageOutDto result = productQueryFacade.getProducts(null, ProductSortType.LATEST, 0, 20);

			// Assert
			assertAll(
				() -> assertThat(result.content()).hasSize(1),
				() -> assertThat(result.totalElements()).isEqualTo(1),
				() -> verify(productQueryService).searchProducts(null, ProductSortType.LATEST, 0, 20)
			);
		}

	}


	@Nested
	@DisplayName("getAdminProduct()")
	class GetAdminProductTest {

		@Test
		@DisplayName("[getAdminProduct()] 관리자 상세 조회 -> AdminProductDetailOutDto 반환. 브랜드명 포함")
		void getAdminProductSuccess() {
			// Arrange
			Product product = createTestProduct();
			Brand brand = createTestBrand();
			given(productQueryService.findById(1L)).willReturn(product);
			given(brandQueryService.getBrandById(1L)).willReturn(brand);

			// Act
			AdminProductDetailOutDto result = productQueryFacade.getAdminProduct(1L);

			// Assert
			assertAll(
				() -> assertThat(result.id()).isEqualTo(1L),
				() -> assertThat(result.brandName()).isEqualTo("나이키"),
				() -> verify(productQueryService).findById(1L)
			);
		}

	}


	@Nested
	@DisplayName("findActiveByIds()")
	class FindActiveByIdsTest {

		@Test
		@DisplayName("[findActiveByIds()] 상품 ID 목록 -> Product 목록 반환. Service에 위임")
		void findActiveByIdsSuccess() {
			// Arrange
			Product product = createTestProduct();
			given(productQueryService.findActiveByIds(List.of(1L))).willReturn(List.of(product));

			// Act
			List<Product> result = productQueryFacade.findActiveByIds(List.of(1L));

			// Assert
			assertAll(
				() -> assertThat(result).hasSize(1),
				() -> verify(productQueryService).findActiveByIds(List.of(1L))
			);
		}

	}


	@Nested
	@DisplayName("getAdminProducts()")
	class GetAdminProductsTest {

		@Test
		@DisplayName("[getAdminProducts()] 관리자 목록 검색 -> AdminProductPageOutDto 반환")
		void getAdminProductsSuccess() {
			// Arrange
			AdminProductOutDto outDto = new AdminProductOutDto(1L, 1L, null, "상품",
				new BigDecimal("10000"), 100L, 0L, null);
			AdminProductPageOutDto pageOutDto = new AdminProductPageOutDto(List.of(outDto), 0, 20, 1);
			given(productQueryService.searchAdminProducts(null, ProductSortType.LATEST, 0, 20)).willReturn(pageOutDto);

			// Act
			AdminProductPageOutDto result = productQueryFacade.getAdminProducts(null, ProductSortType.LATEST, 0, 20);

			// Assert
			assertAll(
				() -> assertThat(result.content()).hasSize(1),
				() -> assertThat(result.totalElements()).isEqualTo(1),
				() -> verify(productQueryService).searchAdminProducts(null, ProductSortType.LATEST, 0, 20)
			);
		}

	}

}
