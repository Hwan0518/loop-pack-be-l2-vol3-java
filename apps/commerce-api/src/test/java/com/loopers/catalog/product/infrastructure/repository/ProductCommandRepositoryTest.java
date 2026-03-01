package com.loopers.catalog.product.infrastructure.repository;


import com.loopers.catalog.product.domain.model.Product;
import com.loopers.catalog.product.domain.repository.ProductCommandRepository;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
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
@DisplayName("ProductCommandRepository 통합 테스트")
class ProductCommandRepositoryTest {

	@Autowired
	private ProductCommandRepository productCommandRepository;


	@Nested
	@DisplayName("save()")
	class SaveTest {

		@Test
		@DisplayName("[save()] 유효한 Product 저장 -> ID가 할당된 Product 반환")
		void saveSuccess() {
			// Arrange
			Product product = Product.create(1L, "테스트 상품", new BigDecimal("10000"), 100L, "설명");

			// Act
			Product savedProduct = productCommandRepository.save(product);

			// Assert
			assertAll(
				() -> assertThat(savedProduct.getId()).isNotNull(),
				() -> assertThat(savedProduct.getBrandId()).isEqualTo(1L),
				() -> assertThat(savedProduct.getName().value()).isEqualTo("테스트 상품"),
				() -> assertThat(savedProduct.getPrice().value()).isEqualByComparingTo(new BigDecimal("10000")),
				() -> assertThat(savedProduct.getStock().value()).isEqualTo(100L),
				() -> assertThat(savedProduct.getDescription().value()).isEqualTo("설명"),
				() -> assertThat(savedProduct.getLikeCount()).isEqualTo(0L),
				() -> assertThat(savedProduct.getDeletedAt()).isNull()
			);
		}


		@Test
		@DisplayName("[save()] description null인 Product 저장 -> description null인 Product 반환")
		void saveWithNullDescription() {
			// Arrange
			Product product = Product.create(1L, "테스트 상품", new BigDecimal("5000"), 50L, null);

			// Act
			Product savedProduct = productCommandRepository.save(product);

			// Assert
			assertAll(
				() -> assertThat(savedProduct.getId()).isNotNull(),
				() -> assertThat(savedProduct.getDescription()).isNull()
			);
		}


		@Test
		@DisplayName("[save()] 기존 Product 수정 후 저장 -> 수정된 Product 반환")
		void saveUpdatedProduct() {
			// Arrange
			Product product = Product.create(1L, "원래 상품", new BigDecimal("10000"), 100L, "설명");
			Product savedProduct = productCommandRepository.save(product);

			// Act
			savedProduct.changeName("수정된 상품");
			savedProduct.changePrice(new BigDecimal("20000"));
			Product updatedProduct = productCommandRepository.save(savedProduct);

			// Assert
			assertAll(
				() -> assertThat(updatedProduct.getId()).isEqualTo(savedProduct.getId()),
				() -> assertThat(updatedProduct.getName().value()).isEqualTo("수정된 상품"),
				() -> assertThat(updatedProduct.getPrice().value()).isEqualByComparingTo(new BigDecimal("20000"))
			);
		}

	}


	@Nested
	@DisplayName("delete()")
	class DeleteTest {

		@Test
		@DisplayName("[delete()] 유효한 Product 삭제 -> soft delete 처리됨")
		void deleteSuccess() {
			// Arrange
			Product product = Product.create(1L, "삭제 상품", new BigDecimal("10000"), 100L, null);
			Product savedProduct = productCommandRepository.save(product);

			// Act
			productCommandRepository.delete(savedProduct);
		}

	}

}
