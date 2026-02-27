package com.loopers.catalog.product.application.facade;


import com.loopers.catalog.brand.application.service.BrandQueryService;
import com.loopers.catalog.brand.domain.model.Brand;
import com.loopers.catalog.brand.domain.model.vo.BrandName;
import com.loopers.catalog.product.application.dto.in.AdminProductCreateInDto;
import com.loopers.catalog.product.application.dto.in.AdminProductUpdateInDto;
import com.loopers.catalog.product.application.dto.out.AdminProductDetailOutDto;
import com.loopers.catalog.product.application.service.ProductCleanupCommandService;
import com.loopers.catalog.product.application.service.ProductCommandService;
import com.loopers.catalog.product.application.service.ProductQueryService;
import com.loopers.catalog.product.domain.model.Product;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
@DisplayName("ProductCommandFacade 단위 테스트")
class ProductCommandFacadeTest {

	@Mock
	private ProductCommandService productCommandService;
	@Mock
	private ProductCleanupCommandService productCleanupCommandService;
	@Mock
	private ProductQueryService productQueryService;
	@Mock
	private BrandQueryService brandQueryService;

	private ProductCommandFacade productCommandFacade;


	@BeforeEach
	void setUp() {
		productCommandFacade = new ProductCommandFacade(
			productCommandService, productCleanupCommandService,
			productQueryService, brandQueryService
		);
	}


	private Product createTestProduct() {
		return Product.reconstruct(1L, 1L,
			ProductName.from("테스트 상품"),
			Money.from(new BigDecimal("10000")),
			Stock.from(100L),
			null, 0L, null);
	}


	private Brand createTestBrand() {
		return Brand.reconstruct(1L, BrandName.from("나이키"), null, null, null);
	}


	@Nested
	@DisplayName("createProduct()")
	class CreateProductTest {

		@Test
		@DisplayName("[createProduct()] 유효한 요청 -> AdminProductDetailOutDto 반환. 브랜드명 포함")
		void createProductSuccess() {
			// Arrange
			AdminProductCreateInDto inDto = new AdminProductCreateInDto(
				1L, "테스트 상품", new BigDecimal("10000"), 100L, "설명"
			);
			Brand brand = createTestBrand();
			Product product = createTestProduct();

			given(brandQueryService.getBrandById(1L)).willReturn(brand);
			given(productCommandService.createProduct(inDto)).willReturn(product);

			// Act
			AdminProductDetailOutDto result = productCommandFacade.createProduct(inDto);

			// Assert
			assertAll(
				() -> assertThat(result.id()).isEqualTo(1L),
				() -> assertThat(result.brandName()).isEqualTo("나이키"),
				() -> assertThat(result.name()).isEqualTo("테스트 상품"),
				() -> verify(brandQueryService).getBrandById(1L),
				() -> verify(productCommandService).createProduct(inDto)
			);
		}

	}


	@Nested
	@DisplayName("updateProduct()")
	class UpdateProductTest {

		@Test
		@DisplayName("[updateProduct()] 유효한 수정 요청 -> AdminProductDetailOutDto 반환")
		void updateProductSuccess() {
			// Arrange
			AdminProductUpdateInDto inDto = new AdminProductUpdateInDto(
				"수정 상품", new BigDecimal("20000"), 200L, "수정 설명"
			);
			Product product = createTestProduct();
			Product updatedProduct = Product.reconstruct(1L, 1L,
				ProductName.from("수정 상품"),
				Money.from(new BigDecimal("20000")),
				Stock.from(200L),
				null, 0L, null);
			Brand brand = createTestBrand();

			given(productQueryService.findActiveById(1L)).willReturn(product);
			given(productCommandService.updateProduct(eq(product), eq(inDto))).willReturn(updatedProduct);
			given(brandQueryService.getBrandById(1L)).willReturn(brand);

			// Act
			AdminProductDetailOutDto result = productCommandFacade.updateProduct(1L, inDto);

			// Assert
			assertAll(
				() -> assertThat(result.name()).isEqualTo("수정 상품"),
				() -> assertThat(result.brandName()).isEqualTo("나이키"),
				() -> verify(productQueryService).findActiveById(1L)
			);
		}

	}


	@Nested
	@DisplayName("deleteProduct()")
	class DeleteProductTest {

		@Test
		@DisplayName("[deleteProduct()] 활성 상품 ID -> 삭제 및 좋아요/장바구니 정리 수행")
		void deleteProductSuccess() {
			// Arrange
			Product product = createTestProduct();
			given(productQueryService.findActiveById(1L)).willReturn(product);
			willDoNothing().given(productCleanupCommandService).deleteAllProductLikes(1L);
			willDoNothing().given(productCleanupCommandService).deleteAllCartItems(1L);

			// Act
			productCommandFacade.deleteProduct(1L);

			// Assert
			assertAll(
				() -> verify(productQueryService).findActiveById(1L),
				() -> verify(productCommandService).deleteProduct(product),
				() -> verify(productCleanupCommandService).deleteAllProductLikes(1L),
				() -> verify(productCleanupCommandService).deleteAllCartItems(1L)
			);
		}

	}


	@Nested
	@DisplayName("increaseLikeCount()")
	class IncreaseLikeCountTest {

		@Test
		@DisplayName("[increaseLikeCount()] 활성 상품 ID -> 좋아요 수 증가. QueryService로 조회 후 CommandService로 증가")
		void increaseLikeCountSuccess() {
			// Arrange
			Product product = createTestProduct();
			given(productQueryService.findActiveById(1L)).willReturn(product);

			// Act
			productCommandFacade.increaseLikeCount(1L);

			// Assert
			assertAll(
				() -> verify(productQueryService).findActiveById(1L),
				() -> verify(productCommandService).increaseLikeCount(product)
			);
		}

	}


	@Nested
	@DisplayName("decreaseLikeCount()")
	class DecreaseLikeCountTest {

		@Test
		@DisplayName("[decreaseLikeCount()] 활성 상품 ID -> 좋아요 수 감소. QueryService로 조회 후 CommandService로 감소")
		void decreaseLikeCountSuccess() {
			// Arrange
			Product product = createTestProduct();
			given(productQueryService.findActiveById(1L)).willReturn(product);

			// Act
			productCommandFacade.decreaseLikeCount(1L);

			// Assert
			assertAll(
				() -> verify(productQueryService).findActiveById(1L),
				() -> verify(productCommandService).decreaseLikeCount(product)
			);
		}

	}

}
