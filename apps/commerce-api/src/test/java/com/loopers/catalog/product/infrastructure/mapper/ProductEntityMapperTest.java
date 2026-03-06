package com.loopers.catalog.product.infrastructure.mapper;


import com.loopers.catalog.product.domain.model.Product;
import com.loopers.catalog.product.domain.model.vo.Money;
import com.loopers.catalog.product.domain.model.vo.ProductDescription;
import com.loopers.catalog.product.domain.model.vo.ProductName;
import com.loopers.catalog.product.domain.model.vo.Stock;
import com.loopers.catalog.product.infrastructure.entity.ProductEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;


@DisplayName("ProductEntityMapper 테스트")
class ProductEntityMapperTest {

	private ProductEntityMapper mapper;


	@BeforeEach
	void setUp() {
		mapper = new ProductEntityMapper();
	}


	@Nested
	@DisplayName("toEntity()")
	class ToEntityTest {

		@Test
		@DisplayName("[toEntity()] 유효한 Product -> ProductEntity 변환 성공. 모든 필드 매핑")
		void toEntitySuccess() {
			// Arrange
			Product product = Product.reconstruct(
				1L, 2L,
				ProductName.from("테스트 상품"),
				Money.from(new BigDecimal("10000")),
				Stock.from(100L),
				ProductDescription.from("설명"),
				5L, 0L, null
			);

			// Act
			ProductEntity entity = mapper.toEntity(product);

			// Assert
			assertAll(
				() -> assertThat(entity.getBrandId()).isEqualTo(2L),
				() -> assertThat(entity.getName()).isEqualTo("테스트 상품"),
				() -> assertThat(entity.getPrice()).isEqualByComparingTo(new BigDecimal("10000")),
				() -> assertThat(entity.getStock()).isEqualTo(100L),
				() -> assertThat(entity.getDescription()).isEqualTo("설명"),
				() -> assertThat(entity.getLikeCount()).isEqualTo(5L)
			);
		}


		@Test
		@DisplayName("[toEntity()] description null인 Product -> description null인 Entity 변환")
		void toEntityWithNullDescription() {
			// Arrange
			Product product = Product.reconstruct(
				1L, 2L,
				ProductName.from("상품"),
				Money.from(BigDecimal.TEN),
				Stock.from(10L),
				null, 0L, 0L, null
			);

			// Act
			ProductEntity entity = mapper.toEntity(product);

			// Assert
			assertThat(entity.getDescription()).isNull();
		}


		@Test
		@DisplayName("[toEntity()] 삭제된 Product -> deletedAt 설정된 Entity 변환")
		void toEntityWithDeleted() {
			// Arrange
			Product product = Product.reconstruct(
				1L, 2L,
				ProductName.from("상품"),
				Money.from(BigDecimal.TEN),
				Stock.from(10L),
				null, 0L, 0L, ZonedDateTime.now()
			);

			// Act
			ProductEntity entity = mapper.toEntity(product);

			// Assert
			assertThat(entity.getDeletedAt()).isNotNull();
		}

	}


	@Nested
	@DisplayName("toDomain()")
	class ToDomainTest {

		@Test
		@DisplayName("[toDomain()] 유효한 ProductEntity -> Product 변환 성공. 모든 필드 매핑")
		void toDomainSuccess() {
			// Arrange
			ProductEntity entity = ProductEntity.of(
				1L, 2L, "테스트 상품", new BigDecimal("10000"), 100L, "설명", 5L, 0L
			);

			// Act
			Product product = mapper.toDomain(entity);

			// Assert
			assertAll(
				() -> assertThat(product.getBrandId()).isEqualTo(2L),
				() -> assertThat(product.getName().value()).isEqualTo("테스트 상품"),
				() -> assertThat(product.getPrice().value()).isEqualByComparingTo(new BigDecimal("10000")),
				() -> assertThat(product.getStock().value()).isEqualTo(100L),
				() -> assertThat(product.getDescription().value()).isEqualTo("설명"),
				() -> assertThat(product.getLikeCount()).isEqualTo(5L)
			);
		}


		@Test
		@DisplayName("[toDomain()] description null인 Entity -> description null인 Product 변환")
		void toDomainWithNullDescription() {
			// Arrange
			ProductEntity entity = ProductEntity.of(
				1L, 2L, "상품", BigDecimal.TEN, 10L, null, 0L, 0L
			);

			// Act
			Product product = mapper.toDomain(entity);

			// Assert
			assertThat(product.getDescription()).isNull();
		}

	}

}
