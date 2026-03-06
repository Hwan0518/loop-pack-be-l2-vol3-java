package com.loopers.catalog.product.application.service;


import com.loopers.catalog.product.application.dto.out.AdminProductOutDto;
import com.loopers.catalog.product.application.dto.out.AdminProductPageOutDto;
import com.loopers.catalog.product.application.dto.out.ProductOutDto;
import com.loopers.catalog.product.application.dto.out.ProductPageOutDto;
import com.loopers.catalog.product.application.port.out.query.ProductQueryPort;
import com.loopers.catalog.product.application.port.out.query.criteria.ProductSearchCriteria;
import com.loopers.catalog.product.domain.model.Product;
import com.loopers.catalog.product.domain.model.enums.ProductSortType;
import com.loopers.catalog.product.domain.model.vo.Money;
import com.loopers.catalog.product.domain.model.vo.ProductName;
import com.loopers.catalog.product.domain.model.vo.Stock;
import com.loopers.catalog.product.domain.repository.ProductQueryRepository;
import com.loopers.catalog.product.domain.repository.vo.PageCriteria;
import com.loopers.catalog.product.domain.repository.vo.PageResult;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;


@ExtendWith(MockitoExtension.class)
@DisplayName("ProductQueryService 단위 테스트")
class ProductQueryServiceTest {

	@Mock
	private ProductQueryRepository productQueryRepository;
	@Mock
	private ProductQueryPort productQueryPort;

	private ProductQueryService productQueryService;


	@BeforeEach
	void setUp() {
		productQueryService = new ProductQueryService(productQueryRepository, productQueryPort);
	}


	private Product createTestProduct() {
		return Product.reconstruct(1L, 1L,
			ProductName.from("테스트 상품"),
			Money.from(new BigDecimal("10000")),
			Stock.from(100L),
			null, 0L, null);
	}


	@Nested
	@DisplayName("findActiveById()")
	class FindActiveByIdTest {

		@Test
		@DisplayName("[findActiveById()] 활성 상품 존재 -> Product 반환")
		void findActiveByIdSuccess() {
			// Arrange
			Product product = createTestProduct();
			given(productQueryRepository.findActiveById(1L)).willReturn(Optional.of(product));

			// Act
			Product result = productQueryService.findActiveById(1L);

			// Assert
			assertAll(
				() -> assertThat(result.getId()).isEqualTo(1L),
				() -> assertThat(result.getName().value()).isEqualTo("테스트 상품")
			);
		}


		@Test
		@DisplayName("[findActiveById()] 상품 없음 -> PRODUCT_NOT_FOUND 예외")
		void findActiveByIdNotFound() {
			// Arrange
			given(productQueryRepository.findActiveById(999L)).willReturn(Optional.empty());

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> productQueryService.findActiveById(999L));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.PRODUCT_NOT_FOUND),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.PRODUCT_NOT_FOUND.getMessage())
			);
		}

	}


	@Nested
	@DisplayName("existsActiveByBrandId()")
	class ExistsActiveByBrandIdTest {

		@Test
		@DisplayName("[existsActiveByBrandId()] 활성 상품 존재 -> true")
		void existsTrue() {
			// Arrange
			given(productQueryRepository.existsActiveByBrandId(1L)).willReturn(true);

			// Act
			boolean result = productQueryService.existsActiveByBrandId(1L);

			// Assert
			assertThat(result).isTrue();
		}


		@Test
		@DisplayName("[existsActiveByBrandId()] 활성 상품 없음 -> false")
		void existsFalse() {
			// Arrange
			given(productQueryRepository.existsActiveByBrandId(1L)).willReturn(false);

			// Act
			boolean result = productQueryService.existsActiveByBrandId(1L);

			// Assert
			assertThat(result).isFalse();
		}

	}


	@Nested
	@DisplayName("findById()")
	class FindByIdTest {

		@Test
		@DisplayName("[findById()] 상품 존재 -> Product 반환 (삭제 포함)")
		void findByIdSuccess() {
			// Arrange
			Product product = createTestProduct();
			given(productQueryRepository.findById(1L)).willReturn(Optional.of(product));

			// Act
			Product result = productQueryService.findById(1L);

			// Assert
			assertThat(result.getId()).isEqualTo(1L);
		}


		@Test
		@DisplayName("[findById()] 상품 없음 -> PRODUCT_NOT_FOUND 예외")
		void findByIdNotFound() {
			// Arrange
			given(productQueryRepository.findById(999L)).willReturn(Optional.empty());

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> productQueryService.findById(999L));

			// Assert
			assertThat(exception.getErrorType()).isEqualTo(ErrorType.PRODUCT_NOT_FOUND);
		}

	}


	@Nested
	@DisplayName("searchProducts()")
	class SearchProductsTest {

		@Test
		@DisplayName("[searchProducts()] 검색 조건으로 조회 -> ProductPageOutDto 반환")
		void searchProductsSuccess() {
			// Arrange
			ProductSearchCriteria criteria = new ProductSearchCriteria(null, ProductSortType.LATEST);
			PageCriteria pageCriteria = new PageCriteria(0, 20);
			ProductOutDto outDto = new ProductOutDto(1L, 1L, null, "상품", new BigDecimal("10000"), 100L, 0L);
			PageResult<ProductOutDto> pageResult = new PageResult<>(List.of(outDto), 0, 20, 1);
			given(productQueryPort.searchProducts(criteria, pageCriteria)).willReturn(pageResult);

			// Act
			ProductPageOutDto result = productQueryService.searchProducts(null, ProductSortType.LATEST, 0, 20);

			// Assert
			assertAll(
				() -> assertThat(result.content()).hasSize(1),
				() -> assertThat(result.totalElements()).isEqualTo(1),
				() -> verify(productQueryPort).searchProducts(criteria, pageCriteria)
			);
		}

	}


	@Nested
	@DisplayName("findActiveByIds()")
	class FindActiveByIdsTest {

		@Test
		@DisplayName("[findActiveByIds()] 활성 상품 ID 목록 -> Product 목록 반환")
		void findActiveByIdsSuccess() {
			// Arrange
			Product product = createTestProduct();
			given(productQueryRepository.findActiveByIds(List.of(1L))).willReturn(List.of(product));

			// Act
			List<Product> result = productQueryService.findActiveByIds(List.of(1L));

			// Assert
			assertAll(
				() -> assertThat(result).hasSize(1),
				() -> assertThat(result.get(0).getId()).isEqualTo(1L)
			);
		}


		@Test
		@DisplayName("[findActiveByIds()] 빈 ID 목록 -> 빈 목록 반환")
		void findActiveByIdsEmpty() {
			// Arrange
			given(productQueryRepository.findActiveByIds(List.of())).willReturn(List.of());

			// Act
			List<Product> result = productQueryService.findActiveByIds(List.of());

			// Assert
			assertThat(result).isEmpty();
		}

	}


	@Nested
	@DisplayName("searchAdminProducts()")
	class SearchAdminProductsTest {

		@Test
		@DisplayName("[searchAdminProducts()] 관리자 검색 조건으로 조회 -> AdminProductPageOutDto 반환")
		void searchAdminProductsSuccess() {
			// Arrange
			ProductSearchCriteria criteria = new ProductSearchCriteria(null, ProductSortType.LATEST);
			PageCriteria pageCriteria = new PageCriteria(0, 20);
			AdminProductOutDto outDto = new AdminProductOutDto(1L, 1L, null, "상품",
				new BigDecimal("10000"), 100L, 0L, null);
			PageResult<AdminProductOutDto> pageResult = new PageResult<>(List.of(outDto), 0, 20, 1);
			given(productQueryPort.searchAdminProducts(criteria, pageCriteria)).willReturn(pageResult);

			// Act
			AdminProductPageOutDto result = productQueryService.searchAdminProducts(null, ProductSortType.LATEST, 0, 20);

			// Assert
			assertAll(
				() -> assertThat(result.content()).hasSize(1),
				() -> assertThat(result.totalElements()).isEqualTo(1),
				() -> verify(productQueryPort).searchAdminProducts(criteria, pageCriteria)
			);
		}

	}

}
