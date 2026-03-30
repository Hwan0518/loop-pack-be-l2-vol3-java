package com.loopers.catalog.product.infrastructure.query;


import com.loopers.catalog.brand.domain.model.enums.VisibleStatus;
import com.loopers.catalog.brand.infrastructure.entity.BrandEntity;
import com.loopers.catalog.brand.infrastructure.jpa.BrandJpaRepository;
import com.loopers.catalog.product.application.dto.out.AdminProductDetailOutDto;
import com.loopers.catalog.product.application.dto.out.AdminProductOutDto;
import com.loopers.catalog.product.application.dto.out.ProductOutDto;
import com.loopers.catalog.product.application.port.out.query.ProductQueryPort;
import com.loopers.catalog.product.application.port.out.cache.dto.IdListCacheEntry;
import com.loopers.catalog.product.application.port.out.cache.dto.ProductCacheDto;
import com.loopers.catalog.product.application.port.out.query.criteria.ProductSearchCriteria;
import com.loopers.catalog.product.domain.model.Product;
import com.loopers.catalog.product.domain.model.enums.ProductSortType;
import com.loopers.catalog.product.domain.model.vo.Money;
import com.loopers.catalog.product.domain.model.vo.ProductDescription;
import com.loopers.catalog.product.domain.model.vo.ProductName;
import com.loopers.catalog.product.domain.model.vo.Stock;
import com.loopers.catalog.product.domain.repository.vo.PageCriteria;
import com.loopers.catalog.product.domain.repository.vo.PageResult;
import com.loopers.catalog.product.infrastructure.entity.ProductEntity;
import com.loopers.catalog.product.infrastructure.entity.ProductReadModelEntity;
import com.loopers.catalog.product.infrastructure.jpa.ProductJpaRepository;
import com.loopers.catalog.product.infrastructure.jpa.ProductReadModelJpaRepository;
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
import java.time.ZonedDateTime;
import java.util.List;

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
	private ProductReadModelJpaRepository productReadModelJpaRepository;

	@Autowired
	private DatabaseCleanUp databaseCleanUp;


	@AfterEach
	void tearDown() {
		databaseCleanUp.truncateAllTables();
	}


	// ProductEntity 저장 후 대응하는 ProductReadModelEntity도 함께 적재하는 헬퍼 (likeCount = 0)
	private ProductEntity saveProductWithReadModel(ProductEntity productEntity, String brandName) {
		return saveProductWithReadModel(productEntity, brandName, 0L);
	}


	// ProductEntity 저장 후 대응하는 ProductReadModelEntity도 함께 적재하는 헬퍼 (likeCount 지정)
	private ProductEntity saveProductWithReadModel(ProductEntity productEntity, String brandName, Long likeCount) {

		// 1. ProductEntity 저장 (ID 자동 생성)
		ProductEntity saved = productJpaRepository.save(productEntity);

		// 2. Product 도메인 모델 reconstruct (from() — 검증 생략)
		Product product = Product.reconstruct(
			saved.getId(),
			saved.getBrandId(),
			ProductName.from(saved.getName()),
			Money.from(saved.getPrice()),
			Stock.from(saved.getStock()),
			ProductDescription.from(saved.getDescription()),
			saved.getDeletedAt()
		);

		// 3. Read Model 엔티티 생성 및 저장 (likeCount 명시 전달)
		productReadModelJpaRepository.save(
			ProductReadModelEntity.of(product, brandName, ZonedDateTime.now(), likeCount));

		return saved;
	}


	// 삭제된 상품의 Read Model도 함께 적재하는 헬퍼 (deletedAt 반영)
	private ProductEntity saveDeletedProductWithReadModel(ProductEntity productEntity, String brandName) {

		// 1. ProductEntity 저장 및 삭제 처리
		ProductEntity saved = productJpaRepository.save(productEntity);
		saved.delete();
		ProductEntity deletedSaved = productJpaRepository.save(saved);

		// 2. Product 도메인 모델 reconstruct (deletedAt 포함)
		Product product = Product.reconstruct(
			deletedSaved.getId(),
			deletedSaved.getBrandId(),
			ProductName.from(deletedSaved.getName()),
			Money.from(deletedSaved.getPrice()),
			Stock.from(deletedSaved.getStock()),
			ProductDescription.from(deletedSaved.getDescription()),
			deletedSaved.getDeletedAt()
		);

		// 3. Read Model 엔티티 생성 및 저장
		productReadModelJpaRepository.save(
			ProductReadModelEntity.of(product, brandName, ZonedDateTime.now(), 0L));

		return deletedSaved;
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
			saveProductWithReadModel(
				ProductEntity.of(brand.getId(), "테스트 상품", new BigDecimal("10000.00"), 100L, "설명"),
				"테스트 브랜드");

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
			saveProductWithReadModel(
				ProductEntity.of(brand1.getId(), "상품A", new BigDecimal("10000.00"), 100L, null),
				"브랜드A");
			saveProductWithReadModel(
				ProductEntity.of(brand2.getId(), "상품B", new BigDecimal("20000.00"), 200L, null),
				"브랜드B");

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
			saveProductWithReadModel(
				ProductEntity.of(brand.getId(), "활성 상품", new BigDecimal("10000.00"), 100L, null),
				"브랜드");
			saveDeletedProductWithReadModel(
				ProductEntity.of(brand.getId(), "삭제 상품", new BigDecimal("20000.00"), 200L, null),
				"브랜드");

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
			saveProductWithReadModel(
				ProductEntity.of(brand.getId(), "비싼 상품", new BigDecimal("50000.00"), 10L, null),
				"브랜드");
			saveProductWithReadModel(
				ProductEntity.of(brand.getId(), "저렴한 상품", new BigDecimal("10000.00"), 20L, null),
				"브랜드");

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
			saveProductWithReadModel(
				ProductEntity.of(brand.getId(), "인기 상품", new BigDecimal("10000.00"), 100L, null),
				"브랜드", 50L);
			saveProductWithReadModel(
				ProductEntity.of(brand.getId(), "일반 상품", new BigDecimal("10000.00"), 100L, null),
				"브랜드", 5L);

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
				saveProductWithReadModel(
					ProductEntity.of(brand.getId(), "상품" + i, new BigDecimal("10000.00"), 100L, null),
					"브랜드");
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
			saveProductWithReadModel(
				ProductEntity.of(999L, "브랜드 없는 상품", new BigDecimal("10000.00"), 100L, null),
				null);

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
			saveProductWithReadModel(
				ProductEntity.of(brand.getId(), "관리자 상품", new BigDecimal("30000.00"), 50L, "설명"),
				"관리자 브랜드", 10L);

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
			saveProductWithReadModel(
				ProductEntity.of(brand.getId(), "활성 상품", new BigDecimal("10000.00"), 100L, null),
				"브랜드");
			saveDeletedProductWithReadModel(
				ProductEntity.of(brand.getId(), "삭제 상품", new BigDecimal("20000.00"), 200L, null),
				"브랜드");

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
			saveProductWithReadModel(
				ProductEntity.of(brand1.getId(), "상품A", new BigDecimal("10000.00"), 100L, null),
				"브랜드A");
			saveProductWithReadModel(
				ProductEntity.of(brand2.getId(), "상품B", new BigDecimal("20000.00"), 200L, null),
				"브랜드B");

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
			saveProductWithReadModel(
				ProductEntity.of(brand.getId(), "비싼 상품", new BigDecimal("50000.00"), 10L, null),
				"브랜드");
			saveProductWithReadModel(
				ProductEntity.of(brand.getId(), "저렴한 상품", new BigDecimal("10000.00"), 20L, null),
				"브랜드");

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


	@Nested
	@DisplayName("searchProductIds()")
	class SearchProductIdsTest {

		@Test
		@DisplayName("[searchProductIds()] 활성 상품 존재 -> IdListCacheEntry(ids, totalElements) 반환. 정렬 적용")
		void searchProductIdsSuccess() {
			// Arrange
			BrandEntity brand = brandJpaRepository.save(
				BrandEntity.of("브랜드", "설명", VisibleStatus.VISIBLE));
			ProductEntity p1 = saveProductWithReadModel(
				ProductEntity.of(brand.getId(), "비싼 상품", new BigDecimal("50000.00"), 10L, null),
				"브랜드");
			ProductEntity p2 = saveProductWithReadModel(
				ProductEntity.of(brand.getId(), "저렴한 상품", new BigDecimal("10000.00"), 20L, null),
				"브랜드");

			ProductSearchCriteria criteria = new ProductSearchCriteria(null, ProductSortType.PRICE_ASC);
			PageCriteria pageCriteria = new PageCriteria(0, 10);

			// Act
			IdListCacheEntry result = productQueryPort.searchProductIds(criteria, pageCriteria);

			// Assert — PRICE_ASC 정렬: 저렴한 상품(p2) → 비싼 상품(p1)
			assertAll(
				() -> assertThat(result.ids()).hasSize(2),
				() -> assertThat(result.ids().get(0)).isEqualTo(p2.getId()),
				() -> assertThat(result.ids().get(1)).isEqualTo(p1.getId()),
				() -> assertThat(result.totalElements()).isEqualTo(2)
			);
		}


		@Test
		@DisplayName("[searchProductIds()] brandId 필터 -> 해당 브랜드의 ID만 반환")
		void searchProductIdsWithBrandFilter() {
			// Arrange
			BrandEntity brand1 = brandJpaRepository.save(
				BrandEntity.of("브랜드A", "설명A", VisibleStatus.VISIBLE));
			BrandEntity brand2 = brandJpaRepository.save(
				BrandEntity.of("브랜드B", "설명B", VisibleStatus.VISIBLE));
			ProductEntity p1 = saveProductWithReadModel(
				ProductEntity.of(brand1.getId(), "상품A", new BigDecimal("10000.00"), 100L, null),
				"브랜드A");
			saveProductWithReadModel(
				ProductEntity.of(brand2.getId(), "상품B", new BigDecimal("20000.00"), 200L, null),
				"브랜드B");

			ProductSearchCriteria criteria = new ProductSearchCriteria(brand1.getId(), null);
			PageCriteria pageCriteria = new PageCriteria(0, 10);

			// Act
			IdListCacheEntry result = productQueryPort.searchProductIds(criteria, pageCriteria);

			// Assert
			assertAll(
				() -> assertThat(result.ids()).hasSize(1),
				() -> assertThat(result.ids().get(0)).isEqualTo(p1.getId()),
				() -> assertThat(result.totalElements()).isEqualTo(1)
			);
		}


		@Test
		@DisplayName("[searchProductIds()] 삭제된 상품 제외 -> 활성 상품 ID만 반환")
		void searchProductIdsExcludesDeleted() {
			// Arrange
			BrandEntity brand = brandJpaRepository.save(
				BrandEntity.of("브랜드", "설명", VisibleStatus.VISIBLE));
			ProductEntity active = saveProductWithReadModel(
				ProductEntity.of(brand.getId(), "활성 상품", new BigDecimal("10000.00"), 100L, null),
				"브랜드");
			saveDeletedProductWithReadModel(
				ProductEntity.of(brand.getId(), "삭제 상품", new BigDecimal("20000.00"), 200L, null),
				"브랜드");

			ProductSearchCriteria criteria = new ProductSearchCriteria(null, null);
			PageCriteria pageCriteria = new PageCriteria(0, 10);

			// Act
			IdListCacheEntry result = productQueryPort.searchProductIds(criteria, pageCriteria);

			// Assert
			assertAll(
				() -> assertThat(result.ids()).hasSize(1),
				() -> assertThat(result.ids().get(0)).isEqualTo(active.getId()),
				() -> assertThat(result.totalElements()).isEqualTo(1)
			);
		}


		@Test
		@DisplayName("[searchProductIds()] 페이지네이션 -> 지정된 페이지 크기만큼 ID 반환. totalElements는 전체 개수")
		void searchProductIdsWithPagination() {
			// Arrange
			BrandEntity brand = brandJpaRepository.save(
				BrandEntity.of("브랜드", "설명", VisibleStatus.VISIBLE));
			for (int i = 1; i <= 5; i++) {
				saveProductWithReadModel(
					ProductEntity.of(brand.getId(), "상품" + i, new BigDecimal("10000.00"), 100L, null),
					"브랜드");
			}

			ProductSearchCriteria criteria = new ProductSearchCriteria(null, null);
			PageCriteria pageCriteria = new PageCriteria(0, 2);

			// Act
			IdListCacheEntry result = productQueryPort.searchProductIds(criteria, pageCriteria);

			// Assert
			assertAll(
				() -> assertThat(result.ids()).hasSize(2),
				() -> assertThat(result.totalElements()).isEqualTo(5)
			);
		}


		@Test
		@DisplayName("[searchProductIds()] 빈 결과 -> ids 빈 목록, totalElements 0")
		void searchProductIdsEmpty() {
			// Arrange
			ProductSearchCriteria criteria = new ProductSearchCriteria(null, null);
			PageCriteria pageCriteria = new PageCriteria(0, 10);

			// Act
			IdListCacheEntry result = productQueryPort.searchProductIds(criteria, pageCriteria);

			// Assert
			assertAll(
				() -> assertThat(result.ids()).isEmpty(),
				() -> assertThat(result.totalElements()).isEqualTo(0)
			);
		}

	}


	@Nested
	@DisplayName("findProductCacheDtoById()")
	class FindProductCacheDtoByIdTest {

		@Test
		@DisplayName("[findProductCacheDtoById()] 활성 상품 ID -> ProductCacheDto 반환. brandName, description 포함")
		void findProductCacheDtoByIdSuccess() {
			// Arrange
			BrandEntity brand = brandJpaRepository.save(
				BrandEntity.of("나이키", "스포츠 브랜드", VisibleStatus.VISIBLE));
			ProductEntity product = saveProductWithReadModel(
				ProductEntity.of(brand.getId(), "에어맥스", new BigDecimal("129000.00"), 50L, "러닝화"),
				"나이키", 10L);

			// Act
			ProductCacheDto result = productQueryPort.findProductCacheDtoById(product.getId());

			// Assert
			assertAll(
				() -> assertThat(result).isNotNull(),
				() -> assertThat(result.id()).isEqualTo(product.getId()),
				() -> assertThat(result.brandId()).isEqualTo(brand.getId()),
				() -> assertThat(result.brandName()).isEqualTo("나이키"),
				() -> assertThat(result.name()).isEqualTo("에어맥스"),
				() -> assertThat(result.price()).isEqualByComparingTo(new BigDecimal("129000.00")),
				() -> assertThat(result.stock()).isEqualTo(50L),
				() -> assertThat(result.description()).isEqualTo("러닝화"),
				() -> assertThat(result.likeCount()).isEqualTo(10L)
			);
		}


		@Test
		@DisplayName("[findProductCacheDtoById()] 삭제된 상품 ID -> null 반환")
		void findProductCacheDtoByIdDeleted() {
			// Arrange
			BrandEntity brand = brandJpaRepository.save(
				BrandEntity.of("브랜드", "설명", VisibleStatus.VISIBLE));
			ProductEntity deleted = saveDeletedProductWithReadModel(
				ProductEntity.of(brand.getId(), "삭제 상품", new BigDecimal("10000.00"), 100L, null),
				"브랜드");

			// Act
			ProductCacheDto result = productQueryPort.findProductCacheDtoById(deleted.getId());

			// Assert
			assertThat(result).isNull();
		}


		@Test
		@DisplayName("[findProductCacheDtoById()] 존재하지 않는 ID -> null 반환")
		void findProductCacheDtoByIdNotFound() {
			// Act
			ProductCacheDto result = productQueryPort.findProductCacheDtoById(999L);

			// Assert
			assertThat(result).isNull();
		}

	}


	@Nested
	@DisplayName("findProductCacheDtosByIds()")
	class FindProductCacheDtosByIdsTest {

		@Test
		@DisplayName("[findProductCacheDtosByIds()] 활성 상품 ID 목록 -> ProductCacheDto 목록 반환")
		void findProductCacheDtosByIdsSuccess() {
			// Arrange
			BrandEntity brand = brandJpaRepository.save(
				BrandEntity.of("나이키", "스포츠 브랜드", VisibleStatus.VISIBLE));
			ProductEntity p1 = saveProductWithReadModel(
				ProductEntity.of(brand.getId(), "에어맥스", new BigDecimal("129000.00"), 50L, "러닝화"),
				"나이키", 10L);
			ProductEntity p2 = saveProductWithReadModel(
				ProductEntity.of(brand.getId(), "에어포스", new BigDecimal("119000.00"), 30L, "캐주얼"),
				"나이키", 20L);

			// Act
			List<ProductCacheDto> result = productQueryPort.findProductCacheDtosByIds(
				List.of(p1.getId(), p2.getId()));

			// Assert
			assertAll(
				() -> assertThat(result).hasSize(2),
				() -> assertThat(result).extracting(ProductCacheDto::name)
					.containsExactlyInAnyOrder("에어맥스", "에어포스")
			);
		}


		@Test
		@DisplayName("[findProductCacheDtosByIds()] 삭제된 상품 포함 ID 목록 -> 활성 상품만 반환")
		void findProductCacheDtosByIdsExcludesDeleted() {
			// Arrange
			BrandEntity brand = brandJpaRepository.save(
				BrandEntity.of("브랜드", "설명", VisibleStatus.VISIBLE));
			ProductEntity active = saveProductWithReadModel(
				ProductEntity.of(brand.getId(), "활성 상품", new BigDecimal("10000.00"), 100L, null),
				"브랜드");
			ProductEntity deleted = saveDeletedProductWithReadModel(
				ProductEntity.of(brand.getId(), "삭제 상품", new BigDecimal("20000.00"), 200L, null),
				"브랜드");

			// Act
			List<ProductCacheDto> result = productQueryPort.findProductCacheDtosByIds(
				List.of(active.getId(), deleted.getId()));

			// Assert — 활성 상품만 반환
			assertAll(
				() -> assertThat(result).hasSize(1),
				() -> assertThat(result.get(0).id()).isEqualTo(active.getId()),
				() -> assertThat(result.get(0).name()).isEqualTo("활성 상품")
			);
		}


		@Test
		@DisplayName("[findProductCacheDtosByIds()] 존재하지 않는 ID만 포함 -> 빈 목록 반환")
		void findProductCacheDtosByIdsAllNotFound() {
			// Act
			List<ProductCacheDto> result = productQueryPort.findProductCacheDtosByIds(List.of(999L, 1000L));

			// Assert
			assertThat(result).isEmpty();
		}

	}


	@Nested
	@DisplayName("findAdminProductDetailById()")
	class FindAdminProductDetailByIdTest {

		@Test
		@DisplayName("[findAdminProductDetailById()] 활성 상품 ID -> AdminProductDetailOutDto 반환. 모든 필드 포함")
		void findAdminProductDetailByIdSuccess() {
			// Arrange
			BrandEntity brand = brandJpaRepository.save(
				BrandEntity.of("나이키", "스포츠 브랜드", VisibleStatus.VISIBLE));
			ProductEntity product = saveProductWithReadModel(
				ProductEntity.of(brand.getId(), "에어맥스", new BigDecimal("129000.00"), 50L, "러닝화"),
				"나이키", 10L);

			// Act
			AdminProductDetailOutDto result = productQueryPort.findAdminProductDetailById(product.getId());

			// Assert
			assertAll(
				() -> assertThat(result).isNotNull(),
				() -> assertThat(result.id()).isEqualTo(product.getId()),
				() -> assertThat(result.brandId()).isEqualTo(brand.getId()),
				() -> assertThat(result.brandName()).isEqualTo("나이키"),
				() -> assertThat(result.name()).isEqualTo("에어맥스"),
				() -> assertThat(result.price()).isEqualByComparingTo(new BigDecimal("129000.00")),
				() -> assertThat(result.stock()).isEqualTo(50L),
				() -> assertThat(result.description()).isEqualTo("러닝화"),
				() -> assertThat(result.likeCount()).isEqualTo(10L),
				() -> assertThat(result.deletedAt()).isNull()
			);
		}


		@Test
		@DisplayName("[findAdminProductDetailById()] 삭제된 상품 ID -> AdminProductDetailOutDto 반환. deletedAt 포함")
		void findAdminProductDetailByIdDeleted() {
			// Arrange
			BrandEntity brand = brandJpaRepository.save(
				BrandEntity.of("아디다스", "스포츠", VisibleStatus.VISIBLE));
			ProductEntity deleted = saveDeletedProductWithReadModel(
				ProductEntity.of(brand.getId(), "삭제 상품", new BigDecimal("50000.00"), 10L, null),
				"아디다스");

			// Act
			AdminProductDetailOutDto result = productQueryPort.findAdminProductDetailById(deleted.getId());

			// Assert
			assertAll(
				() -> assertThat(result).isNotNull(),
				() -> assertThat(result.id()).isEqualTo(deleted.getId()),
				() -> assertThat(result.brandName()).isEqualTo("아디다스"),
				() -> assertThat(result.name()).isEqualTo("삭제 상품"),
				() -> assertThat(result.deletedAt()).isNotNull()
			);
		}


		@Test
		@DisplayName("[findAdminProductDetailById()] 존재하지 않는 ID -> null 반환")
		void findAdminProductDetailByIdNotFound() {
			// Act
			AdminProductDetailOutDto result = productQueryPort.findAdminProductDetailById(999L);

			// Assert
			assertThat(result).isNull();
		}

	}

}
