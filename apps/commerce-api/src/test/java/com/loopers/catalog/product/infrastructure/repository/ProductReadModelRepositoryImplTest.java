package com.loopers.catalog.product.infrastructure.repository;


import com.loopers.catalog.brand.domain.model.enums.VisibleStatus;
import com.loopers.catalog.brand.infrastructure.entity.BrandEntity;
import com.loopers.catalog.brand.infrastructure.jpa.BrandJpaRepository;
import com.loopers.catalog.product.domain.model.Product;
import com.loopers.catalog.product.domain.model.vo.Money;
import com.loopers.catalog.product.domain.model.vo.ProductDescription;
import com.loopers.catalog.product.domain.model.vo.ProductName;
import com.loopers.catalog.product.domain.model.vo.Stock;
import com.loopers.catalog.product.domain.repository.ProductReadModelRepository;
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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;


@SpringBootTest
@ActiveProfiles("test")
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
@DisplayName("ProductReadModelRepositoryImpl 통합 테스트")
class ProductReadModelRepositoryImplTest {

	@Autowired
	private ProductReadModelRepository productReadModelRepository;

	@Autowired
	private ProductReadModelJpaRepository productReadModelJpaRepository;

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
	@DisplayName("save()")
	class SaveTest {

		@Test
		@Transactional
		@DisplayName("[save()] 기존 Read Model 업데이트 -> createdAt/likeCount 보존, mutable field만 갱신")
		void saveExistingReadModel_preservesCreatedAtAndLikeCount() {
			BrandEntity brand = brandJpaRepository.save(
				BrandEntity.of("기존 브랜드", "설명", VisibleStatus.VISIBLE));
			ProductEntity productEntity = productJpaRepository.save(
				ProductEntity.of(brand.getId(), "원래 상품", new BigDecimal("10000.00"), 100L, "원래 설명"));
			Product product = reconstructProduct(productEntity);
			ZonedDateTime originalCreatedAt = ZonedDateTime.parse("2025-01-01T00:00:00Z");

			productReadModelJpaRepository.save(
				ProductReadModelEntity.of(product, "기존 브랜드", originalCreatedAt, 7L));

			Product updatedProduct = Product.reconstruct(
				productEntity.getId(),
				brand.getId(),
				ProductName.create("수정 상품"),
				Money.create(new BigDecimal("15000.00")),
				Stock.create(55L),
				ProductDescription.create("수정 설명"),
				null
			);

			productReadModelRepository.save(updatedProduct, "수정 브랜드");

			ProductReadModelEntity result = productReadModelJpaRepository.findById(productEntity.getId()).orElseThrow();

			assertAll(
				() -> assertThat(result.getBrandName()).isEqualTo("수정 브랜드"),
				() -> assertThat(result.getName()).isEqualTo("수정 상품"),
				() -> assertThat(result.getPrice()).isEqualByComparingTo(new BigDecimal("15000.00")),
				() -> assertThat(result.getStock()).isEqualTo(55L),
				() -> assertThat(result.getDescription()).isEqualTo("수정 설명"),
				() -> assertThat(result.getCreatedAt()).isEqualTo(originalCreatedAt),
				() -> assertThat(result.getLikeCount()).isEqualTo(7L)
			);
		}


		@Test
		@Transactional
		@DisplayName("[save()] Read Model 미존재 상품 저장 -> 신규 row 생성")
		void saveNewReadModel_insertsRow() {
			BrandEntity brand = brandJpaRepository.save(
				BrandEntity.of("신규 브랜드", "설명", VisibleStatus.VISIBLE));
			ProductEntity productEntity = productJpaRepository.save(
				ProductEntity.of(brand.getId(), "신규 상품", new BigDecimal("21000.00"), 33L, "신규 설명"));

			productReadModelRepository.save(reconstructProduct(productEntity), "신규 브랜드");

			ProductReadModelEntity result = productReadModelJpaRepository.findById(productEntity.getId()).orElseThrow();

			assertAll(
				() -> assertThat(result.getBrandId()).isEqualTo(brand.getId()),
				() -> assertThat(result.getBrandName()).isEqualTo("신규 브랜드"),
				() -> assertThat(result.getName()).isEqualTo("신규 상품"),
				() -> assertThat(result.getPrice()).isEqualByComparingTo(new BigDecimal("21000.00")),
				() -> assertThat(result.getStock()).isEqualTo(33L),
				() -> assertThat(result.getLikeCount()).isZero(),
				() -> assertThat(result.getCreatedAt()).isNotNull()
			);
		}
	}


	private Product reconstructProduct(ProductEntity entity) {
		return Product.reconstruct(
			entity.getId(),
			entity.getBrandId(),
			ProductName.from(entity.getName()),
			Money.from(entity.getPrice()),
			Stock.from(entity.getStock()),
			ProductDescription.from(entity.getDescription()),
			entity.getDeletedAt()
		);
	}
}
