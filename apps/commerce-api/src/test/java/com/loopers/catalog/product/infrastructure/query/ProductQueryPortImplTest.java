package com.loopers.catalog.product.infrastructure.query;


import com.loopers.catalog.brand.domain.model.enums.VisibleStatus;
import com.loopers.catalog.brand.infrastructure.entity.BrandEntity;
import com.loopers.catalog.brand.infrastructure.jpa.BrandJpaRepository;
import com.loopers.catalog.product.application.dto.out.AdminProductOutDto;
import com.loopers.catalog.product.application.dto.out.ProductOutDto;
import com.loopers.catalog.product.application.port.out.query.ProductQueryPort;
import com.loopers.catalog.product.application.port.out.query.criteria.ProductSearchCriteria;
import com.loopers.catalog.product.domain.model.enums.ProductSortType;
import com.loopers.catalog.product.domain.repository.vo.PageCriteria;
import com.loopers.catalog.product.domain.repository.vo.PageResult;
import com.loopers.catalog.product.infrastructure.entity.ProductEntity;
import com.loopers.catalog.product.infrastructure.jpa.ProductJpaRepository;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;


@SpringBootTest
@ActiveProfiles("test")
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
@DisplayName("ProductQueryPortImpl 통합 테스트")
class ProductQueryPortImplTest {

	@Autowired
	private ProductQueryPort productQueryPort;

	@Autowired
	private ProductJpaRepository productJpaRepository;

	@Autowired
	private BrandJpaRepository brandJpaRepository;

	@Autowired
	private DatabaseCleanUp databaseCleanUp;


	@AfterEach
	void tearDown() {
		databaseCleanUp.truncateAllTables();
	}


	@Nested
	@DisplayName("searchProducts()")
	class SearchProductsTest {

		@Test
		@DisplayName("[searchProducts()] 브랜드가 있는 활성 상품 조회 -> brandName이 포함된 결과 반환")
		void searchProductsWithBrandName() {
			// Arrange
			BrandEntity brand = brandJpaRepository.save(
				BrandEntity.of("테스트 브랜드", "브랜드 설명", VisibleStatus.VISIBLE));
			productJpaRepository.save(
				ProductEntity.of(brand.getId(), "테스트 상품", new BigDecimal("10000.00"), 100L, "설명", 0L));

			ProductSearchCriteria criteria = new ProductSearchCriteria(null, null);
			PageCriteria pageCriteria = new PageCriteria(0, 10);

			// Act
			PageResult<ProductOutDto> result = productQueryPort.searchProducts(criteria, pageCriteria);

			// Assert
			assertAll(
				() -> assertThat(result.content()).hasSize(1),
				() -> assertThat(result.content().get(0).brandName()).isEqualTo("테스트 브랜드"),
				() -> assertThat(result.content().get(0).name()).isEqualTo("테스트 상품"),
				() -> assertThat(result.content().get(0).brandId()).isEqualTo(brand.getId()),
				() -> assertThat(result.totalElements()).isEqualTo(1)
			);
		}


		@Test
		@DisplayName("[searchProducts()] brandId 필터 적용 -> 해당 브랜드의 상품만 반환")
		void searchProductsWithBrandIdFilter() {
			// Arrange
			BrandEntity brand1 = brandJpaRepository.save(
				BrandEntity.of("브랜드A", "설명A", VisibleStatus.VISIBLE));
			BrandEntity brand2 = brandJpaRepository.save(
				BrandEntity.of("브랜드B", "설명B", VisibleStatus.VISIBLE));
			productJpaRepository.save(
				ProductEntity.of(brand1.getId(), "상품A", new BigDecimal("10000.00"), 100L, null, 0L));
			productJpaRepository.save(
				ProductEntity.of(brand2.getId(), "상품B", new BigDecimal("20000.00"), 200L, null, 0L));

			ProductSearchCriteria criteria = new ProductSearchCriteria(brand1.getId(), null);
			PageCriteria pageCriteria = new PageCriteria(0, 10);

			// Act
			PageResult<ProductOutDto> result = productQueryPort.searchProducts(criteria, pageCriteria);

			// Assert
			assertAll(
				() -> assertThat(result.content()).hasSize(1),
				() -> assertThat(result.content().get(0).brandName()).isEqualTo("브랜드A"),
				() -> assertThat(result.content().get(0).name()).isEqualTo("상품A"),
				() -> assertThat(result.totalElements()).isEqualTo(1)
			);
		}


		@Test
		@DisplayName("[searchProducts()] 삭제된 상품 -> 활성 상품만 반환 (삭제된 상품 제외)")
		void searchProductsExcludesDeleted() {
			// Arrange
			BrandEntity brand = brandJpaRepository.save(
				BrandEntity.of("브랜드", "설명", VisibleStatus.VISIBLE));
			productJpaRepository.save(
				ProductEntity.of(brand.getId(), "활성 상품", new BigDecimal("10000.00"), 100L, null, 0L));
			ProductEntity deletedProduct = productJpaRepository.save(
				ProductEntity.of(brand.getId(), "삭제 상품", new BigDecimal("20000.00"), 200L, null, 0L));
			deletedProduct.delete();
			productJpaRepository.save(deletedProduct);

			ProductSearchCriteria criteria = new ProductSearchCriteria(null, null);
			PageCriteria pageCriteria = new PageCriteria(0, 10);

			// Act
			PageResult<ProductOutDto> result = productQueryPort.searchProducts(criteria, pageCriteria);

			// Assert
			assertAll(
				() -> assertThat(result.content()).hasSize(1),
				() -> assertThat(result.content().get(0).name()).isEqualTo("활성 상품"),
				() -> assertThat(result.totalElements()).isEqualTo(1)
			);
		}


		@Test
		@DisplayName("[searchProducts()] PRICE_ASC 정렬 -> 가격 낮은순으로 반환")
		void searchProductsSortByPriceAsc() {
			// Arrange
			BrandEntity brand = brandJpaRepository.save(
				BrandEntity.of("브랜드", "설명", VisibleStatus.VISIBLE));
			productJpaRepository.save(
				ProductEntity.of(brand.getId(), "비싼 상품", new BigDecimal("50000.00"), 10L, null, 0L));
			productJpaRepository.save(
				ProductEntity.of(brand.getId(), "저렴한 상품", new BigDecimal("10000.00"), 20L, null, 0L));

			ProductSearchCriteria criteria = new ProductSearchCriteria(null, ProductSortType.PRICE_ASC);
			PageCriteria pageCriteria = new PageCriteria(0, 10);

			// Act
			PageResult<ProductOutDto> result = productQueryPort.searchProducts(criteria, pageCriteria);

			// Assert
			assertAll(
				() -> assertThat(result.content()).hasSize(2),
				() -> assertThat(result.content().get(0).name()).isEqualTo("저렴한 상품"),
				() -> assertThat(result.content().get(1).name()).isEqualTo("비싼 상품")
			);
		}


		@Test
		@DisplayName("[searchProducts()] LIKES_DESC 정렬 -> 좋아요 많은순으로 반환")
		void searchProductsSortByLikesDesc() {
			// Arrange
			BrandEntity brand = brandJpaRepository.save(
				BrandEntity.of("브랜드", "설명", VisibleStatus.VISIBLE));
			productJpaRepository.save(
				ProductEntity.of(brand.getId(), "인기 상품", new BigDecimal("10000.00"), 100L, null, 50L));
			productJpaRepository.save(
				ProductEntity.of(brand.getId(), "일반 상품", new BigDecimal("10000.00"), 100L, null, 5L));

			ProductSearchCriteria criteria = new ProductSearchCriteria(null, ProductSortType.LIKES_DESC);
			PageCriteria pageCriteria = new PageCriteria(0, 10);

			// Act
			PageResult<ProductOutDto> result = productQueryPort.searchProducts(criteria, pageCriteria);

			// Assert
			assertAll(
				() -> assertThat(result.content()).hasSize(2),
				() -> assertThat(result.content().get(0).name()).isEqualTo("인기 상품"),
				() -> assertThat(result.content().get(1).name()).isEqualTo("일반 상품")
			);
		}


		@Test
		@DisplayName("[searchProducts()] 페이지네이션 -> 지정된 페이지 크기만큼 반환")
		void searchProductsWithPagination() {
			// Arrange
			BrandEntity brand = brandJpaRepository.save(
				BrandEntity.of("브랜드", "설명", VisibleStatus.VISIBLE));
			for (int i = 1; i <= 5; i++) {
				productJpaRepository.save(
					ProductEntity.of(brand.getId(), "상품" + i, new BigDecimal("10000.00"), 100L, null, 0L));
			}

			ProductSearchCriteria criteria = new ProductSearchCriteria(null, null);
			PageCriteria pageCriteria = new PageCriteria(0, 2);

			// Act
			PageResult<ProductOutDto> result = productQueryPort.searchProducts(criteria, pageCriteria);

			// Assert
			assertAll(
				() -> assertThat(result.content()).hasSize(2),
				() -> assertThat(result.totalElements()).isEqualTo(5),
				() -> assertThat(result.page()).isEqualTo(0),
				() -> assertThat(result.size()).isEqualTo(2)
			);
		}


		@Test
		@DisplayName("[searchProducts()] 브랜드가 없는 상품 (brandId가 존재하지 않는 브랜드) -> brandName null 반환")
		void searchProductsWithNonExistentBrand() {
			// Arrange
			productJpaRepository.save(
				ProductEntity.of(999L, "브랜드 없는 상품", new BigDecimal("10000.00"), 100L, null, 0L));

			ProductSearchCriteria criteria = new ProductSearchCriteria(null, null);
			PageCriteria pageCriteria = new PageCriteria(0, 10);

			// Act
			PageResult<ProductOutDto> result = productQueryPort.searchProducts(criteria, pageCriteria);

			// Assert
			assertAll(
				() -> assertThat(result.content()).hasSize(1),
				() -> assertThat(result.content().get(0).brandName()).isNull(),
				() -> assertThat(result.content().get(0).name()).isEqualTo("브랜드 없는 상품")
			);
		}


		@Test
		@DisplayName("[searchProducts()] 상품이 없는 경우 -> 빈 결과 반환")
		void searchProductsEmpty() {
			// Arrange
			ProductSearchCriteria criteria = new ProductSearchCriteria(null, null);
			PageCriteria pageCriteria = new PageCriteria(0, 10);

			// Act
			PageResult<ProductOutDto> result = productQueryPort.searchProducts(criteria, pageCriteria);

			// Assert
			assertAll(
				() -> assertThat(result.content()).isEmpty(),
				() -> assertThat(result.totalElements()).isEqualTo(0)
			);
		}

	}


	@Nested
	@DisplayName("searchAdminProducts()")
	class SearchAdminProductsTest {

		@Test
		@DisplayName("[searchAdminProducts()] 브랜드가 있는 상품 조회 -> brandName이 포함된 결과 반환")
		void searchAdminProductsWithBrandName() {
			// Arrange
			BrandEntity brand = brandJpaRepository.save(
				BrandEntity.of("관리자 브랜드", "설명", VisibleStatus.VISIBLE));
			productJpaRepository.save(
				ProductEntity.of(brand.getId(), "관리자 상품", new BigDecimal("30000.00"), 50L, "설명", 10L));

			ProductSearchCriteria criteria = new ProductSearchCriteria(null, null);
			PageCriteria pageCriteria = new PageCriteria(0, 10);

			// Act
			PageResult<AdminProductOutDto> result = productQueryPort.searchAdminProducts(criteria, pageCriteria);

			// Assert
			assertAll(
				() -> assertThat(result.content()).hasSize(1),
				() -> assertThat(result.content().get(0).brandName()).isEqualTo("관리자 브랜드"),
				() -> assertThat(result.content().get(0).name()).isEqualTo("관리자 상품"),
				() -> assertThat(result.content().get(0).brandId()).isEqualTo(brand.getId()),
				() -> assertThat(result.totalElements()).isEqualTo(1)
			);
		}


		@Test
		@DisplayName("[searchAdminProducts()] 삭제된 상품 포함 -> 전체 상품 반환 (deletedAt 포함)")
		void searchAdminProductsIncludesDeleted() {
			// Arrange
			BrandEntity brand = brandJpaRepository.save(
				BrandEntity.of("브랜드", "설명", VisibleStatus.VISIBLE));
			productJpaRepository.save(
				ProductEntity.of(brand.getId(), "활성 상품", new BigDecimal("10000.00"), 100L, null, 0L));
			ProductEntity deletedProduct = productJpaRepository.save(
				ProductEntity.of(brand.getId(), "삭제 상품", new BigDecimal("20000.00"), 200L, null, 0L));
			deletedProduct.delete();
			productJpaRepository.save(deletedProduct);

			ProductSearchCriteria criteria = new ProductSearchCriteria(null, null);
			PageCriteria pageCriteria = new PageCriteria(0, 10);

			// Act
			PageResult<AdminProductOutDto> result = productQueryPort.searchAdminProducts(criteria, pageCriteria);

			// Assert
			assertAll(
				() -> assertThat(result.content()).hasSize(2),
				() -> assertThat(result.totalElements()).isEqualTo(2)
			);
		}


		@Test
		@DisplayName("[searchAdminProducts()] brandId 필터 적용 -> 해당 브랜드의 상품만 반환")
		void searchAdminProductsWithBrandIdFilter() {
			// Arrange
			BrandEntity brand1 = brandJpaRepository.save(
				BrandEntity.of("브랜드A", "설명A", VisibleStatus.VISIBLE));
			BrandEntity brand2 = brandJpaRepository.save(
				BrandEntity.of("브랜드B", "설명B", VisibleStatus.VISIBLE));
			productJpaRepository.save(
				ProductEntity.of(brand1.getId(), "상품A", new BigDecimal("10000.00"), 100L, null, 0L));
			productJpaRepository.save(
				ProductEntity.of(brand2.getId(), "상품B", new BigDecimal("20000.00"), 200L, null, 0L));

			ProductSearchCriteria criteria = new ProductSearchCriteria(brand2.getId(), null);
			PageCriteria pageCriteria = new PageCriteria(0, 10);

			// Act
			PageResult<AdminProductOutDto> result = productQueryPort.searchAdminProducts(criteria, pageCriteria);

			// Assert
			assertAll(
				() -> assertThat(result.content()).hasSize(1),
				() -> assertThat(result.content().get(0).brandName()).isEqualTo("브랜드B"),
				() -> assertThat(result.content().get(0).name()).isEqualTo("상품B"),
				() -> assertThat(result.totalElements()).isEqualTo(1)
			);
		}


		@Test
		@DisplayName("[searchAdminProducts()] PRICE_ASC 정렬 -> 가격 낮은순으로 반환")
		void searchAdminProductsSortByPriceAsc() {
			// Arrange
			BrandEntity brand = brandJpaRepository.save(
				BrandEntity.of("브랜드", "설명", VisibleStatus.VISIBLE));
			productJpaRepository.save(
				ProductEntity.of(brand.getId(), "비싼 상품", new BigDecimal("50000.00"), 10L, null, 0L));
			productJpaRepository.save(
				ProductEntity.of(brand.getId(), "저렴한 상품", new BigDecimal("10000.00"), 20L, null, 0L));

			ProductSearchCriteria criteria = new ProductSearchCriteria(null, ProductSortType.PRICE_ASC);
			PageCriteria pageCriteria = new PageCriteria(0, 10);

			// Act
			PageResult<AdminProductOutDto> result = productQueryPort.searchAdminProducts(criteria, pageCriteria);

			// Assert
			assertAll(
				() -> assertThat(result.content()).hasSize(2),
				() -> assertThat(result.content().get(0).name()).isEqualTo("저렴한 상품"),
				() -> assertThat(result.content().get(1).name()).isEqualTo("비싼 상품")
			);
		}


		@Test
		@DisplayName("[searchAdminProducts()] 상품이 없는 경우 -> 빈 결과 반환")
		void searchAdminProductsEmpty() {
			// Arrange
			ProductSearchCriteria criteria = new ProductSearchCriteria(null, null);
			PageCriteria pageCriteria = new PageCriteria(0, 10);

			// Act
			PageResult<AdminProductOutDto> result = productQueryPort.searchAdminProducts(criteria, pageCriteria);

			// Assert
			assertAll(
				() -> assertThat(result.content()).isEmpty(),
				() -> assertThat(result.totalElements()).isEqualTo(0)
			);
		}

	}

}
