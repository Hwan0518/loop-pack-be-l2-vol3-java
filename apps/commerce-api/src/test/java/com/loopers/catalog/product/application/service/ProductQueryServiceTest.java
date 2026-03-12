package com.loopers.catalog.product.application.service;


import com.loopers.catalog.product.application.dto.out.*;
import com.loopers.catalog.product.application.port.out.query.ProductQueryPort;
import com.loopers.catalog.product.application.port.out.query.criteria.ProductSearchCriteria;
import com.loopers.catalog.product.domain.model.Product;
import com.loopers.catalog.product.domain.model.enums.ProductSortType;
import com.loopers.catalog.product.domain.model.vo.Money;
import com.loopers.catalog.product.domain.model.vo.ProductName;
import com.loopers.catalog.product.domain.model.vo.Stock;
import com.loopers.catalog.product.domain.repository.ProductQueryRepository;
import com.loopers.catalog.product.domain.repository.ProductReadModelRepository;
import com.loopers.catalog.product.domain.repository.vo.PageCriteria;
import com.loopers.catalog.product.domain.repository.vo.PageResult;
import com.loopers.catalog.product.infrastructure.cache.IdListCacheEntry;
import com.loopers.catalog.product.infrastructure.cache.ProductCacheDto;
import com.loopers.catalog.product.infrastructure.cache.ProductCacheManager;
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
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;


@ExtendWith(MockitoExtension.class)
@DisplayName("ProductQueryService 단위 테스트")
class ProductQueryServiceTest {

	@Mock
	private ProductQueryRepository productQueryRepository;
	@Mock
	private ProductReadModelRepository productReadModelRepository;
	@Mock
	private ProductQueryPort productQueryPort;
	@Mock
	private ProductCacheManager productCacheManager;

	private ProductQueryService productQueryService;


	@BeforeEach
	void setUp() {
		productQueryService = new ProductQueryService(
			productQueryRepository, productReadModelRepository, productQueryPort, productCacheManager
		);
	}


	private Product createTestProduct() {
		return Product.reconstruct(1L, 1L,
			ProductName.from("테스트 상품"),
			Money.from(new BigDecimal("10000")),
			Stock.from(100L),
			null, null);
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
		@DisplayName("[searchProducts()] 캐시 불가 조건 (page >= MAX_CACHEABLE_PAGE) -> DB 직접 조회. 캐시 미사용")
		void searchProductsNotCacheable() {
			// Arrange — page=2 (MAX_CACHEABLE_PAGE=2 이상)
			ProductSearchCriteria criteria = new ProductSearchCriteria(null, ProductSortType.LATEST);
			PageCriteria pageCriteria = new PageCriteria(2, 20);
			ProductOutDto outDto = new ProductOutDto(1L, 1L, null, "상품", new BigDecimal("10000"), 100L, 0L);
			PageResult<ProductOutDto> pageResult = new PageResult<>(List.of(outDto), 2, 20, 50);
			given(productQueryPort.searchProducts(criteria, pageCriteria)).willReturn(pageResult);

			// Act
			ProductPageOutDto result = productQueryService.searchProducts(null, ProductSortType.LATEST, 2, 20);

			// Assert
			assertAll(
				() -> assertThat(result.content()).hasSize(1),
				() -> assertThat(result.page()).isEqualTo(2),
				() -> verify(productQueryPort).searchProducts(criteria, pageCriteria),
				() -> verifyNoInteractions(productCacheManager)
			);
		}


		@Test
		@DisplayName("[searchProducts()] 2계층 캐시 전체 히트 -> ID 리스트 캐시 + MGET 상세 캐시 모두 히트. DB 미호출")
		@SuppressWarnings("unchecked")
		void searchProductsFullCacheHit() {
			// Arrange — Layer 1: ID 리스트 캐시 히트
			IdListCacheEntry idList = new IdListCacheEntry(List.of(1L, 2L), 2);
			given(productCacheManager.getOrLoad(
				eq("products:ids:v1:all:LATEST:0:20"),
				eq(IdListCacheEntry.class),
				any(Duration.class),
				any(Supplier.class)
			)).willReturn(idList);

			// Layer 2: MGET 상세 캐시 전체 히트
			ProductCacheDto dto1 = new ProductCacheDto(1L, 1L, "나이키", "상품1", new BigDecimal("10000"), 100L, null, 0L);
			ProductCacheDto dto2 = new ProductCacheDto(2L, 1L, "나이키", "상품2", new BigDecimal("20000"), 50L, null, 5L);
			given(productCacheManager.mgetProductDetails(List.of(1L, 2L))).willReturn(List.of(dto1, dto2));

			// Act
			ProductPageOutDto result = productQueryService.searchProducts(null, ProductSortType.LATEST, 0, 20);

			// Assert
			assertAll(
				() -> assertThat(result.content()).hasSize(2),
				() -> assertThat(result.content().get(0).name()).isEqualTo("상품1"),
				() -> assertThat(result.content().get(1).name()).isEqualTo("상품2"),
				() -> assertThat(result.totalElements()).isEqualTo(2),
				() -> verifyNoInteractions(productQueryPort)
			);
		}


		@Test
		@DisplayName("[searchProducts()] MGET partial miss -> miss된 ID를 DB에서 조회 후 캐시 저장. ID 순서 보존")
		@SuppressWarnings("unchecked")
		void searchProductsMgetPartialMiss() {
			// Arrange — Layer 1: ID 리스트 캐시 히트
			IdListCacheEntry idList = new IdListCacheEntry(List.of(1L, 2L), 2);
			given(productCacheManager.getOrLoad(
				eq("products:ids:v1:1:LATEST:0:20"),
				eq(IdListCacheEntry.class),
				any(Duration.class),
				any(Supplier.class)
			)).willReturn(idList);

			// Layer 2: MGET — id=1 히트, id=2 miss (null)
			ProductCacheDto dto1 = new ProductCacheDto(1L, 1L, "나이키", "상품1", new BigDecimal("10000"), 100L, null, 0L);
			given(productCacheManager.mgetProductDetails(List.of(1L, 2L))).willReturn(Arrays.asList(dto1, null));

			// miss된 id=2를 DB에서 조회
			ProductCacheDto dto2 = new ProductCacheDto(2L, 1L, "나이키", "상품2", new BigDecimal("20000"), 50L, null, 5L);
			given(productQueryPort.findProductCacheDtosByIds(List.of(2L))).willReturn(List.of(dto2));

			// Act
			ProductPageOutDto result = productQueryService.searchProducts(1L, ProductSortType.LATEST, 0, 20);

			// Assert — ID 순서 보존 (1 → 2)
			assertAll(
				() -> assertThat(result.content()).hasSize(2),
				() -> assertThat(result.content().get(0).id()).isEqualTo(1L),
				() -> assertThat(result.content().get(1).id()).isEqualTo(2L),
				() -> verify(productQueryPort).findProductCacheDtosByIds(List.of(2L)),
				() -> verify(productCacheManager).put(eq("product:v1:2"), eq(dto2), any(Duration.class))
			);
		}


		@Test
		@DisplayName("[searchProducts()] 빈 ID 리스트 -> 빈 페이지 반환. Layer 2 MGET 미호출")
		@SuppressWarnings("unchecked")
		void searchProductsEmptyIdList() {
			// Arrange — Layer 1: 빈 ID 리스트
			IdListCacheEntry idList = new IdListCacheEntry(List.of(), 0);
			given(productCacheManager.getOrLoad(
				eq("products:ids:v1:all:LATEST:0:20"),
				eq(IdListCacheEntry.class),
				any(Duration.class),
				any(Supplier.class)
			)).willReturn(idList);

			// Act
			ProductPageOutDto result = productQueryService.searchProducts(null, ProductSortType.LATEST, 0, 20);

			// Assert
			assertAll(
				() -> assertThat(result.content()).isEmpty(),
				() -> assertThat(result.totalElements()).isEqualTo(0),
				() -> verify(productCacheManager).getOrLoad(any(), any(), any(), any())
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
		@DisplayName("[searchAdminProducts()] 관리자 검색 -> 캐시 미적용. 항상 QueryPort 호출. AdminProductPageOutDto 반환")
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
				() -> verify(productQueryPort).searchAdminProducts(criteria, pageCriteria),
				() -> verifyNoInteractions(productCacheManager)
			);
		}

	}


	@Nested
	@DisplayName("getOrLoadProductDetail()")
	class GetOrLoadProductDetailTest {

		@Test
		@DisplayName("[getOrLoadProductDetail()] 캐시 히트 -> ProductCacheDto에서 ProductDetailOutDto로 변환 반환. QueryPort 미호출")
		void cacheHit() {
			// Arrange
			ProductCacheDto cacheDto = new ProductCacheDto(
				1L, 1L, "나이키", "테스트 상품", new BigDecimal("10000"), 100L, null, 0L);
			given(productCacheManager.getOrLoadWithPer(
				eq("product:v1:1"),
				eq(ProductCacheDto.class),
				any(Duration.class),
				any(Supplier.class)
			)).willReturn(cacheDto);

			// Act
			ProductDetailOutDto result = productQueryService.getOrLoadProductDetail(1L);

			// Assert
			assertAll(
				() -> assertThat(result.id()).isEqualTo(1L),
				() -> assertThat(result.brandName()).isEqualTo("나이키"),
				() -> assertThat(result.name()).isEqualTo("테스트 상품"),
				() -> verifyNoInteractions(productQueryPort)
			);
		}


		@Test
		@DisplayName("[getOrLoadProductDetail()] 상품 미존재 (loader가 null 반환) -> PRODUCT_NOT_FOUND 예외")
		@SuppressWarnings("unchecked")
		void productNotFound() {
			// Arrange — loader가 null 반환 (상품 미존재 또는 삭제)
			given(productCacheManager.getOrLoadWithPer(
				eq("product:v1:999"),
				eq(ProductCacheDto.class),
				any(Duration.class),
				any(Supplier.class)
			)).willReturn(null);

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> productQueryService.getOrLoadProductDetail(999L));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.PRODUCT_NOT_FOUND),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.PRODUCT_NOT_FOUND.getMessage())
			);
		}


		@Test
		@DisplayName("[getOrLoadProductDetail()] 캐시 미스 -> QueryPort.findProductCacheDtoById() 호출 후 캐시 저장. ProductDetailOutDto 반환")
		@SuppressWarnings("unchecked")
		void cacheMiss() {
			// Arrange
			ProductCacheDto cacheDto = new ProductCacheDto(
				1L, 1L, "나이키", "테스트 상품", new BigDecimal("10000"), 100L, null, 0L);
			given(productQueryPort.findProductCacheDtoById(1L)).willReturn(cacheDto);

			// loader 실행을 시뮬레이션 (캐시 미스 시 supplier 호출)
			given(productCacheManager.getOrLoadWithPer(
				eq("product:v1:1"),
				eq(ProductCacheDto.class),
				any(Duration.class),
				any(Supplier.class)
			)).willAnswer(invocation -> {
				Supplier<ProductCacheDto> loader = invocation.getArgument(3);
				return loader.get();
			});

			// Act
			ProductDetailOutDto result = productQueryService.getOrLoadProductDetail(1L);

			// Assert
			assertAll(
				() -> assertThat(result.id()).isEqualTo(1L),
				() -> assertThat(result.brandName()).isEqualTo("나이키"),
				() -> assertThat(result.name()).isEqualTo("테스트 상품"),
				() -> verify(productQueryPort).findProductCacheDtoById(1L)
			);
		}

	}


	@Nested
	@DisplayName("getAdminProductDetail()")
	class GetAdminProductDetailTest {

		@Test
		@DisplayName("[getAdminProductDetail()] Read Model에 상품 존재 -> AdminProductDetailOutDto 반환. 삭제된 브랜드에도 안전")
		void getAdminProductDetailSuccess() {
			// Arrange
			AdminProductDetailOutDto detailOutDto = new AdminProductDetailOutDto(
				1L, 1L, "나이키", "테스트 상품", new BigDecimal("10000"), 100L, null, 0L, null);
			given(productQueryPort.findAdminProductDetailById(1L)).willReturn(detailOutDto);

			// Act
			AdminProductDetailOutDto result = productQueryService.getAdminProductDetail(1L);

			// Assert
			assertAll(
				() -> assertThat(result.id()).isEqualTo(1L),
				() -> assertThat(result.brandName()).isEqualTo("나이키"),
				() -> assertThat(result.name()).isEqualTo("테스트 상품"),
				() -> verify(productQueryPort).findAdminProductDetailById(1L)
			);
		}


		@Test
		@DisplayName("[getAdminProductDetail()] Read Model에 상품 미존재 -> PRODUCT_NOT_FOUND 예외")
		void getAdminProductDetailNotFound() {
			// Arrange
			given(productQueryPort.findAdminProductDetailById(999L)).willReturn(null);

			// Act
			CoreException exception = assertThrows(CoreException.class,
				() -> productQueryService.getAdminProductDetail(999L));

			// Assert
			assertAll(
				() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.PRODUCT_NOT_FOUND),
				() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.PRODUCT_NOT_FOUND.getMessage())
			);
		}

	}


	@Nested
	@DisplayName("findActiveIdsByBrandId()")
	class FindActiveIdsByBrandIdTest {

		@Test
		@DisplayName("[findActiveIdsByBrandId()] 브랜드 ID -> 활성 상품 ID 목록 반환. ReadModelRepository에 위임")
		void findActiveIdsByBrandIdSuccess() {
			// Arrange
			given(productReadModelRepository.findActiveIdsByBrandId(1L)).willReturn(List.of(10L, 20L, 30L));

			// Act
			List<Long> result = productQueryService.findActiveIdsByBrandId(1L);

			// Assert
			assertAll(
				() -> assertThat(result).containsExactly(10L, 20L, 30L),
				() -> verify(productReadModelRepository).findActiveIdsByBrandId(1L)
			);
		}

	}

}
