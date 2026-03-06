package com.loopers.catalog.product.application.service;


import com.loopers.catalog.brand.domain.model.enums.VisibleStatus;
import com.loopers.catalog.brand.infrastructure.entity.BrandEntity;
import com.loopers.catalog.brand.infrastructure.jpa.BrandJpaRepository;
import com.loopers.catalog.product.infrastructure.entity.ProductEntity;
import com.loopers.catalog.product.infrastructure.jpa.ProductJpaRepository;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;


@SpringBootTest
@ActiveProfiles("test")
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
@DisplayName("ProductLikeCount 동시성 통합 테스트")
class ProductLikeCountConcurrencyTest {

	@Autowired
	private ProductCommandService productCommandService;

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


	@Test
	@DisplayName("[increaseLikeCount()] 동시 좋아요 수 증가 10건 -> likeCount 정확히 10. 원자적 카운터로 Lost Update 방지")
	void concurrentIncreaseLikeCount() throws Exception {
		// Arrange
		BrandEntity brand = brandJpaRepository.save(
			BrandEntity.of("테스트 브랜드", "설명", VisibleStatus.VISIBLE));
		ProductEntity product = productJpaRepository.save(
			ProductEntity.of(brand.getId(), "테스트 상품", new BigDecimal("10000.00"), 100L, "설명", 0L));
		Long productId = product.getId();

		int threadCount = 10;
		ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
		List<Future<?>> futures = new ArrayList<>();

		// Act
		for (int i = 0; i < threadCount; i++) {
			futures.add(executorService.submit(() ->
				productCommandService.increaseLikeCount(productId)));
		}
		for (Future<?> future : futures) {
			future.get(10, TimeUnit.SECONDS);
		}
		executorService.shutdown();

		// Assert
		ProductEntity result = productJpaRepository.findById(productId).orElseThrow();
		assertThat(result.getLikeCount()).isEqualTo(10L);
	}


	@Test
	@DisplayName("[decreaseLikeCount()] 동시 좋아요 수 감소 10건 -> likeCount 정확히 0. 원자적 카운터로 음수 방지")
	void concurrentDecreaseLikeCount() throws Exception {
		// Arrange
		BrandEntity brand = brandJpaRepository.save(
			BrandEntity.of("테스트 브랜드", "설명", VisibleStatus.VISIBLE));
		ProductEntity product = productJpaRepository.save(
			ProductEntity.of(brand.getId(), "테스트 상품", new BigDecimal("10000.00"), 100L, "설명", 10L));
		Long productId = product.getId();

		int threadCount = 10;
		ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
		List<Future<?>> futures = new ArrayList<>();

		// Act
		for (int i = 0; i < threadCount; i++) {
			futures.add(executorService.submit(() ->
				productCommandService.decreaseLikeCount(productId)));
		}
		for (Future<?> future : futures) {
			future.get(10, TimeUnit.SECONDS);
		}
		executorService.shutdown();

		// Assert
		ProductEntity result = productJpaRepository.findById(productId).orElseThrow();
		assertThat(result.getLikeCount()).isEqualTo(0L);
	}


	@Test
	@DisplayName("[increaseLikeCount()] 동시 좋아요 수 증가 50건 -> likeCount 정확히 50. 높은 경합에서도 원자적 카운터 정확성 보장")
	void concurrentIncreaseLikeCountHighContention() throws Exception {
		// Arrange
		BrandEntity brand = brandJpaRepository.save(
			BrandEntity.of("테스트 브랜드", "설명", VisibleStatus.VISIBLE));
		ProductEntity product = productJpaRepository.save(
			ProductEntity.of(brand.getId(), "테스트 상품", new BigDecimal("10000.00"), 100L, "설명", 0L));
		Long productId = product.getId();

		int threadCount = 50;
		ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
		List<Future<?>> futures = new ArrayList<>();

		// Act
		for (int i = 0; i < threadCount; i++) {
			futures.add(executorService.submit(() ->
				productCommandService.increaseLikeCount(productId)));
		}
		for (Future<?> future : futures) {
			future.get(10, TimeUnit.SECONDS);
		}
		executorService.shutdown();

		// Assert
		ProductEntity result = productJpaRepository.findById(productId).orElseThrow();
		assertThat(result.getLikeCount()).isEqualTo(50L);
	}

}
