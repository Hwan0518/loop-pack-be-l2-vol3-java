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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;

import static org.assertj.core.api.Assertions.assertThat;


@SpringBootTest
@ActiveProfiles("test")
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
@DisplayName("ProductStock 동시성 통합 테스트")
class ProductStockConcurrencyTest {

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
	@DisplayName("[decreaseStock()] 동시 재고 차감 10건 -> 재고 정확히 차감. 비관적 락으로 Lost Update 방지")
	void concurrentDecreaseStock() throws Exception {
		// Arrange
		BrandEntity brand = brandJpaRepository.save(
			BrandEntity.of("테스트 브랜드", "설명", VisibleStatus.VISIBLE));
		ProductEntity product = productJpaRepository.save(
			ProductEntity.of(brand.getId(), "테스트 상품", new BigDecimal("10000.00"), 100L, "설명"));
		Long productId = product.getId();

		int threadCount = 10;
		ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
		List<Future<?>> futures = new ArrayList<>();

		// Act
		for (int i = 0; i < threadCount; i++) {
			futures.add(executorService.submit(() ->
				productCommandService.decreaseStock(productId, 1L)));
		}
		for (Future<?> future : futures) {
			future.get(10, TimeUnit.SECONDS);
		}
		executorService.shutdown();

		// Assert
		ProductEntity result = productJpaRepository.findById(productId).orElseThrow();
		assertThat(result.getStock()).isEqualTo(90L);
	}


	@Test
	@DisplayName("[decreaseStock()] 재고 부족 시 동시 차감 -> 일부 PRODUCT_OUT_OF_STOCK 예외. 성공 건수만큼만 재고 차감")
	void concurrentDecreaseStockInsufficientStock() throws Exception {
		// Arrange
		BrandEntity brand = brandJpaRepository.save(
			BrandEntity.of("테스트 브랜드", "설명", VisibleStatus.VISIBLE));
		ProductEntity product = productJpaRepository.save(
			ProductEntity.of(brand.getId(), "테스트 상품", new BigDecimal("10000.00"), 5L, "설명"));
		Long productId = product.getId();

		int threadCount = 10;
		ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
		List<Future<?>> futures = new ArrayList<>();

		// Act
		for (int i = 0; i < threadCount; i++) {
			futures.add(executorService.submit(() ->
				productCommandService.decreaseStock(productId, 1L)));
		}

		int successCount = 0;
		int failCount = 0;
		for (Future<?> future : futures) {
			try {
				future.get(10, TimeUnit.SECONDS);
				successCount++;
			} catch (ExecutionException e) {
				// 실패 원인이 PRODUCT_OUT_OF_STOCK인지 검증
				assertThat(e.getCause()).isInstanceOf(CoreException.class);
				assertThat(((CoreException) e.getCause()).getErrorType())
					.isEqualTo(ErrorType.PRODUCT_OUT_OF_STOCK);
				failCount++;
			}
		}
		executorService.shutdown();

		// Assert
		ProductEntity result = productJpaRepository.findById(productId).orElseThrow();
		assertThat(successCount).isEqualTo(5);
		assertThat(failCount).isEqualTo(5);
		assertThat(result.getStock()).isEqualTo(0L);
	}

}
