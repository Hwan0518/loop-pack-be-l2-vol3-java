package com.loopers.catalog.product.infrastructure.repository;


import com.loopers.catalog.product.domain.model.Product;
import com.loopers.catalog.product.domain.repository.ProductCommandRepository;
import com.loopers.catalog.product.domain.repository.ProductQueryRepository;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;


@SpringBootTest
@ActiveProfiles("test")
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
@DisplayName("ProductQueryRepository 통합 테스트")
class ProductQueryRepositoryTest {

	@Autowired
	private ProductQueryRepository productQueryRepository;

	@Autowired
	private ProductCommandRepository productCommandRepository;

	@Autowired
	private DatabaseCleanUp databaseCleanUp;


	@AfterEach
	void tearDown() {
		databaseCleanUp.truncateAllTables();
	}


	@Nested
	@DisplayName("findActiveById()")
	class FindActiveByIdTest {

		@Test
		@DisplayName("[findActiveById()] 활성 상품 ID -> 상품 반환")
		void findActiveByIdSuccess() {
			// Arrange
			Product product = Product.create(1L, "테스트 상품", new BigDecimal("10000"), 100L, "설명");
			Product saved = productCommandRepository.save(product);

			// Act
			Optional<Product> result = productQueryRepository.findActiveById(saved.getId());

			// Assert
			assertAll(
				() -> assertThat(result).isPresent(),
				() -> assertThat(result.get().getName().value()).isEqualTo("테스트 상품")
			);
		}


		@Test
		@DisplayName("[findActiveById()] 삭제된 상품 ID -> Optional.empty() 반환")
		void findActiveByIdDeleted() {
			// Arrange
			Product product = Product.create(1L, "삭제 상품", new BigDecimal("10000"), 100L, null);
			Product saved = productCommandRepository.save(product);
			productCommandRepository.delete(saved);

			// Act
			Optional<Product> result = productQueryRepository.findActiveById(saved.getId());

			// Assert
			assertThat(result).isEmpty();
		}


		@Test
		@DisplayName("[findActiveById()] 존재하지 않는 ID -> Optional.empty() 반환")
		void findActiveByIdNotFound() {
			// Act
			Optional<Product> result = productQueryRepository.findActiveById(999L);

			// Assert
			assertThat(result).isEmpty();
		}

	}


	@Nested
	@DisplayName("existsActiveByBrandId()")
	class ExistsActiveByBrandIdTest {

		@Test
		@DisplayName("[existsActiveByBrandId()] 활성 상품 존재 -> true")
		void existsActiveProduct() {
			// Arrange
			Product product = Product.create(1L, "테스트 상품", new BigDecimal("10000"), 100L, null);
			productCommandRepository.save(product);

			// Act
			boolean exists = productQueryRepository.existsActiveByBrandId(1L);

			// Assert
			assertThat(exists).isTrue();
		}


		@Test
		@DisplayName("[existsActiveByBrandId()] 활성 상품 없음 -> false")
		void noActiveProduct() {
			// Act
			boolean exists = productQueryRepository.existsActiveByBrandId(1L);

			// Assert
			assertThat(exists).isFalse();
		}


		@Test
		@DisplayName("[existsActiveByBrandId()] 삭제된 상품만 존재 -> false")
		void onlyDeletedProducts() {
			// Arrange
			Product product = Product.create(1L, "삭제 상품", new BigDecimal("10000"), 100L, null);
			Product saved = productCommandRepository.save(product);
			productCommandRepository.delete(saved);

			// Act
			boolean exists = productQueryRepository.existsActiveByBrandId(1L);

			// Assert
			assertThat(exists).isFalse();
		}

	}


	@Nested
	@DisplayName("findActiveByIdForUpdate()")
	class FindActiveByIdForUpdateTest {

		@Test
		@Transactional
		@DisplayName("[findActiveByIdForUpdate()] 활성 상품 ID -> 비관적 쓰기 락으로 상품 반환")
		void findActiveByIdForUpdateSuccess() {
			// Arrange
			Product product = Product.create(1L, "락 테스트 상품", new BigDecimal("10000"), 100L, "설명");
			Product saved = productCommandRepository.save(product);

			// Act
			Optional<Product> result = productQueryRepository.findActiveByIdForUpdate(saved.getId());

			// Assert
			assertAll(
				() -> assertThat(result).isPresent(),
				() -> assertThat(result.get().getName().value()).isEqualTo("락 테스트 상품")
			);
		}


		@Test
		@Transactional
		@DisplayName("[findActiveByIdForUpdate()] 삭제된 상품 ID -> Optional.empty() 반환")
		void findActiveByIdForUpdateDeleted() {
			// Arrange
			Product product = Product.create(1L, "삭제 상품", new BigDecimal("10000"), 100L, null);
			Product saved = productCommandRepository.save(product);
			productCommandRepository.delete(saved);

			// Act
			Optional<Product> result = productQueryRepository.findActiveByIdForUpdate(saved.getId());

			// Assert
			assertThat(result).isEmpty();
		}


		@Test
		@Transactional
		@DisplayName("[findActiveByIdForUpdate()] 존재하지 않는 ID -> Optional.empty() 반환")
		void findActiveByIdForUpdateNotFound() {
			// Act
			Optional<Product> result = productQueryRepository.findActiveByIdForUpdate(999L);

			// Assert
			assertThat(result).isEmpty();
		}

	}


	@Nested
	@DisplayName("findActiveByIds()")
	class FindActiveByIdsTest {

		@Test
		@DisplayName("[findActiveByIds()] 활성 상품 ID 목록 -> 해당 상품 목록 반환")
		void findActiveByIdsSuccess() {
			// Arrange
			Product p1 = Product.create(1L, "상품1", new BigDecimal("10000"), 100L, null);
			Product p2 = Product.create(1L, "상품2", new BigDecimal("20000"), 50L, null);
			Product saved1 = productCommandRepository.save(p1);
			Product saved2 = productCommandRepository.save(p2);

			// Act
			List<Product> result = productQueryRepository.findActiveByIds(
				List.of(saved1.getId(), saved2.getId()));

			// Assert
			assertThat(result).hasSize(2);
		}


		@Test
		@DisplayName("[findActiveByIds()] 삭제된 상품 포함 -> 활성 상품만 반환")
		void findActiveByIdsExcludesDeleted() {
			// Arrange
			Product active = Product.create(1L, "활성 상품", new BigDecimal("10000"), 100L, null);
			Product deleted = Product.create(1L, "삭제 상품", new BigDecimal("20000"), 50L, null);
			Product savedActive = productCommandRepository.save(active);
			Product savedDeleted = productCommandRepository.save(deleted);
			productCommandRepository.delete(savedDeleted);

			// Act
			List<Product> result = productQueryRepository.findActiveByIds(
				List.of(savedActive.getId(), savedDeleted.getId()));

			// Assert
			assertAll(
				() -> assertThat(result).hasSize(1),
				() -> assertThat(result.get(0).getName().value()).isEqualTo("활성 상품")
			);
		}


		@Test
		@DisplayName("[findActiveByIds()] 존재하지 않는 ID 포함 -> 존재하는 활성 상품만 반환")
		void findActiveByIdsIgnoresNonExistent() {
			// Arrange
			Product product = Product.create(1L, "상품", new BigDecimal("10000"), 100L, null);
			Product saved = productCommandRepository.save(product);

			// Act
			List<Product> result = productQueryRepository.findActiveByIds(
				List.of(saved.getId(), 999L));

			// Assert
			assertThat(result).hasSize(1);
		}


		@Test
		@DisplayName("[findActiveByIds()] 빈 ID 목록 -> 빈 목록 반환")
		void findActiveByIdsEmptyIds() {
			// Act
			List<Product> result = productQueryRepository.findActiveByIds(List.of());

			// Assert
			assertThat(result).isEmpty();
		}

	}


	@Nested
	@DisplayName("findById()")
	class FindByIdTest {

		@Test
		@DisplayName("[findById()] 활성 상품 ID -> 상품 반환")
		void findByIdActive() {
			// Arrange
			Product product = Product.create(1L, "상품", new BigDecimal("10000"), 100L, null);
			Product saved = productCommandRepository.save(product);

			// Act
			Optional<Product> result = productQueryRepository.findById(saved.getId());

			// Assert
			assertThat(result).isPresent();
		}


		@Test
		@DisplayName("[findById()] 삭제된 상품 ID -> 삭제된 상품 반환 (삭제 포함)")
		void findByIdDeleted() {
			// Arrange
			Product product = Product.create(1L, "상품", new BigDecimal("10000"), 100L, null);
			Product saved = productCommandRepository.save(product);
			productCommandRepository.delete(saved);

			// Act
			Optional<Product> result = productQueryRepository.findById(saved.getId());

			// Assert
			assertAll(
				() -> assertThat(result).isPresent(),
				() -> assertThat(result.get().isDeleted()).isTrue()
			);
		}


		@Test
		@DisplayName("[findById()] 존재하지 않는 ID -> Optional.empty() 반환")
		void findByIdNotFound() {
			// Act
			Optional<Product> result = productQueryRepository.findById(999L);

			// Assert
			assertThat(result).isEmpty();
		}

	}

}
