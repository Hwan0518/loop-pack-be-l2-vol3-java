package com.loopers.catalog.product.infrastructure.cache;


import com.loopers.catalog.product.application.dto.out.ProductDetailOutDto;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;


@SpringBootTest
@ActiveProfiles("test")
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
@DisplayName("CacheStampede 통합 테스트")
class CacheStampedeTest {

	@Autowired
	private ProductCacheManager productCacheManager;

	@Autowired
	private RedisCleanUp redisCleanUp;


	@AfterEach
	void tearDown() {
		redisCleanUp.truncateAll();
	}


	@Nested
	@DisplayName("getOrLoad() 스탬피드")
	class GetOrLoadStampedeTest {

		@Test
		@DisplayName("[getOrLoad()] single-key 스탬피드 - 캐시 미스 상태에서 100개 동시 요청 -> loader 호출 최소화 (이상: 1회)")
		void singleKeyStampede_loaderMinimized() throws InterruptedException {

			// Arrange
			int threadCount = 100;
			ExecutorService executor = Executors.newFixedThreadPool(threadCount);
			CountDownLatch readyLatch = new CountDownLatch(threadCount);
			CountDownLatch startLatch = new CountDownLatch(1);
			CountDownLatch doneLatch = new CountDownLatch(threadCount);
			AtomicInteger loaderCallCount = new AtomicInteger(0);
			String key = "product:stampede:1";
			Duration ttl = Duration.ofMinutes(10);
			ProductDetailOutDto expected = new ProductDetailOutDto(
				1L, 1L, "Brand", "Product",
				new BigDecimal("10000"), 10L, "desc", 0L
			);

			// Act
			for (int i = 0; i < threadCount; i++) {
				executor.submit(() -> {
					readyLatch.countDown();
					try {
						startLatch.await();
						productCacheManager.getOrLoad(
							key, ProductDetailOutDto.class, ttl,
							() -> {
								loaderCallCount.incrementAndGet();

								// DB 조회 시뮬레이션
								try {
									Thread.sleep(50);
								} catch (InterruptedException e) {
									Thread.currentThread().interrupt();
								}

								return expected;
							}
						);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					} finally {
						doneLatch.countDown();
					}
				});
			}

			readyLatch.await();
			startLatch.countDown();
			doneLatch.await();
			executor.shutdown();

			// Assert — loader 호출 최소화 (이상: 1회, 레이스 컨디션 허용: <= 2)
			assertThat(loaderCallCount.get()).isLessThanOrEqualTo(2);
		}


		@Test
		@DisplayName("[getOrLoad()] 캐시 히트 상태에서 100개 동시 요청 -> loader 0회 호출")
		void cacheHitStampede_loaderNotCalled() throws InterruptedException {

			// Arrange
			String key = "product:stampede:2";
			Duration ttl = Duration.ofMinutes(10);
			ProductDetailOutDto cached = new ProductDetailOutDto(
				2L, 1L, "Brand", "CachedProduct",
				new BigDecimal("20000"), 20L, "cached", 5L
			);
			productCacheManager.put(key, cached, ttl);

			int threadCount = 100;
			ExecutorService executor = Executors.newFixedThreadPool(threadCount);
			CountDownLatch readyLatch = new CountDownLatch(threadCount);
			CountDownLatch startLatch = new CountDownLatch(1);
			CountDownLatch doneLatch = new CountDownLatch(threadCount);
			AtomicInteger loaderCallCount = new AtomicInteger(0);

			// Act
			for (int i = 0; i < threadCount; i++) {
				executor.submit(() -> {
					readyLatch.countDown();
					try {
						startLatch.await();
						productCacheManager.getOrLoad(
							key, ProductDetailOutDto.class, ttl,
							() -> {
								loaderCallCount.incrementAndGet();
								return new ProductDetailOutDto(
									99L, 99L, "New", "New",
									new BigDecimal("99999"), 99L, "new", 99L
								);
							}
						);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					} finally {
						doneLatch.countDown();
					}
				});
			}

			readyLatch.await();
			startLatch.countDown();
			doneLatch.await();
			executor.shutdown();

			// Assert — 캐시 히트이므로 loader 0회 호출
			assertThat(loaderCallCount.get()).isEqualTo(0);
		}

	}

}
