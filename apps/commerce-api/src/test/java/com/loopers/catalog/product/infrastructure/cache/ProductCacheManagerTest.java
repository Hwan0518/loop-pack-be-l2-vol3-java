package com.loopers.catalog.product.infrastructure.cache;


import com.loopers.catalog.product.application.dto.out.ProductDetailOutDto;
import com.loopers.catalog.product.application.dto.out.ProductOutDto;
import com.loopers.catalog.product.application.dto.out.ProductPageOutDto;
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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.loopers.catalog.product.infrastructure.cache.ProductCacheConstants.DETAIL_KEY_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;


@SpringBootTest
@ActiveProfiles("test")
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
@DisplayName("ProductCacheManager 통합 테스트")
class ProductCacheManagerTest {

	@Autowired
	private ProductCacheManager productCacheManager;

	@Autowired
	private RedisCleanUp redisCleanUp;

	@Autowired
	private RedisTemplate<String, String> redisTemplate;


	@AfterEach
	void tearDown() {
		redisCleanUp.truncateAll();
	}


	@Nested
	@DisplayName("put() + get()")
	class PutAndGetTest {

		@Test
		@DisplayName("[put() -> get()] ProductDetailOutDto 저장 후 조회 -> 동일한 객체 반환. 직렬화/역직렬화 정상 동작")
		void putAndGetProductDetailOutDto() {

			// Arrange
			String key = "product:1";
			ProductDetailOutDto dto = new ProductDetailOutDto(
				1L, 1L, "TestBrand", "TestProduct",
				new BigDecimal("59900.00"), 100L, "description", 50L
			);

			// Act
			productCacheManager.put(key, dto, Duration.ofMinutes(10));
			Optional<ProductDetailOutDto> result = productCacheManager.get(key, ProductDetailOutDto.class);

			// Assert
			assertAll(
				() -> assertThat(result).isPresent(),
				() -> assertThat(result.get().id()).isEqualTo(1L),
				() -> assertThat(result.get().brandId()).isEqualTo(1L),
				() -> assertThat(result.get().brandName()).isEqualTo("TestBrand"),
				() -> assertThat(result.get().name()).isEqualTo("TestProduct"),
				() -> assertThat(result.get().price()).isEqualByComparingTo(new BigDecimal("59900.00")),
				() -> assertThat(result.get().stock()).isEqualTo(100L),
				() -> assertThat(result.get().description()).isEqualTo("description"),
				() -> assertThat(result.get().likeCount()).isEqualTo(50L)
			);
		}


		@Test
		@DisplayName("[put() -> get()] ProductPageOutDto 저장 후 조회 -> 제네릭 타입 역직렬화 정상 동작")
		void putAndGetProductPageOutDto() {

			// Arrange
			String key = "products:ids:v1:all:LATEST:0:20";
			ProductOutDto productOutDto = new ProductOutDto(
				1L, 1L, "TestBrand", "TestProduct",
				new BigDecimal("10000"), 50L, 10L
			);
			ProductPageOutDto dto = new ProductPageOutDto(
				List.of(productOutDto), 0, 20, 100L
			);

			// Act
			productCacheManager.put(key, dto, Duration.ofMinutes(5));
			Optional<ProductPageOutDto> result = productCacheManager.get(key, ProductPageOutDto.class);

			// Assert
			assertAll(
				() -> assertThat(result).isPresent(),
				() -> assertThat(result.get().content()).hasSize(1),
				() -> assertThat(result.get().content().get(0).id()).isEqualTo(1L),
				() -> assertThat(result.get().content().get(0).brandName()).isEqualTo("TestBrand"),
				() -> assertThat(result.get().page()).isEqualTo(0),
				() -> assertThat(result.get().size()).isEqualTo(20),
				() -> assertThat(result.get().totalElements()).isEqualTo(100L)
			);
		}


		@Test
		@DisplayName("[put() -> get()] BigDecimal price 저장 후 조회 -> compareTo 기준 동일 값 반환")
		void putAndGetBigDecimalPrecision() {

			// Arrange
			String key = "product:2";
			ProductDetailOutDto dto = new ProductDetailOutDto(
				2L, 1L, "Brand", "Product",
				new BigDecimal("99999.99"), 10L, "desc", 0L
			);

			// Act
			productCacheManager.put(key, dto, Duration.ofMinutes(10));
			Optional<ProductDetailOutDto> result = productCacheManager.get(key, ProductDetailOutDto.class);

			// Assert
			assertThat(result).isPresent();
			assertThat(result.get().price()).isEqualByComparingTo(new BigDecimal("99999.99"));
		}


		@Test
		@DisplayName("[put() -> get()] null description 필드 포함 DTO -> 직렬화/역직렬화 정상 동작")
		void putAndGetWithNullDescription() {

			// Arrange
			String key = "product:3";
			ProductDetailOutDto dto = new ProductDetailOutDto(
				3L, 1L, "Brand", "Product",
				new BigDecimal("10000"), 10L, null, 0L
			);

			// Act
			productCacheManager.put(key, dto, Duration.ofMinutes(10));
			Optional<ProductDetailOutDto> result = productCacheManager.get(key, ProductDetailOutDto.class);

			// Assert
			assertAll(
				() -> assertThat(result).isPresent(),
				() -> assertThat(result.get().id()).isEqualTo(3L),
				() -> assertThat(result.get().description()).isNull()
			);
		}

	}


	@Nested
	@DisplayName("evict()")
	class EvictTest {

		@Test
		@DisplayName("[evict()] 저장 후 삭제 -> get() 시 Optional.empty() 반환")
		void evictRemovesCachedValue() {

			// Arrange
			String key = "product:10";
			ProductDetailOutDto dto = new ProductDetailOutDto(
				10L, 1L, "Brand", "Product",
				new BigDecimal("10000"), 10L, "desc", 0L
			);
			productCacheManager.put(key, dto, Duration.ofMinutes(10));

			// Act
			productCacheManager.evict(key);

			// Assert
			Optional<ProductDetailOutDto> result = productCacheManager.get(key, ProductDetailOutDto.class);
			assertThat(result).isEmpty();
		}

	}


	@Nested
	@DisplayName("TTL")
	class TtlTest {

		@Test
		@DisplayName("[put()] TTL 저장 -> base TTL ± 10% 범위 내 TTL 설정 확인")
		void putSetsTtlWithJitter() {

			// Arrange
			String key = "product:ttl";
			Duration baseTtl = Duration.ofMinutes(10);
			long baseMs = baseTtl.toMillis();
			long minMs = baseMs;
			long maxMs = baseMs + (baseMs / 10);

			// Act
			productCacheManager.put(key, "value", baseTtl);

			// Assert
			Long remainMs = redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);
			assertThat(remainMs).isNotNull();
			assertThat(remainMs).isBetween(minMs - 1000, maxMs + 1000);
		}

	}


	@Nested
	@DisplayName("get() — 미스")
	class GetMissTest {

		@Test
		@DisplayName("[get()] 존재하지 않는 키 조회 -> Optional.empty() 반환. 예외 없음")
		void getNonExistentKeyReturnsEmpty() {

			// Act
			Optional<ProductDetailOutDto> result = productCacheManager.get(
				"nonexistent:key", ProductDetailOutDto.class
			);

			// Assert
			assertThat(result).isEmpty();
		}

	}


	@Nested
	@DisplayName("getOrLoad()")
	class GetOrLoadTest {

		@Test
		@DisplayName("[getOrLoad()] 캐시 미스 -> loader 1회 호출 + 캐시 저장")
		void getOrLoadCacheMiss_loaderCalledOnce() {

			// Arrange
			String key = "product:load:1";
			AtomicInteger loaderCallCount = new AtomicInteger(0);
			ProductDetailOutDto expected = new ProductDetailOutDto(
				1L, 1L, "Brand", "Product",
				new BigDecimal("10000"), 10L, "desc", 0L
			);

			// Act
			ProductDetailOutDto result = productCacheManager.getOrLoad(
				key, ProductDetailOutDto.class, Duration.ofMinutes(10),
				() -> {
					loaderCallCount.incrementAndGet();
					return expected;
				}
			);

			// Assert — loader 1회 호출 + 캐시에 저장됨
			assertAll(
				() -> assertThat(loaderCallCount.get()).isEqualTo(1),
				() -> assertThat(result.id()).isEqualTo(1L),
				() -> assertThat(productCacheManager.get(key, ProductDetailOutDto.class)).isPresent()
			);
		}


		@Test
		@DisplayName("[getOrLoad()] 캐시 히트 -> loader 미호출. 캐시된 값 반환")
		void getOrLoadCacheHit_loaderNotCalled() {

			// Arrange
			String key = "product:load:2";
			ProductDetailOutDto cached = new ProductDetailOutDto(
				2L, 1L, "Brand", "CachedProduct",
				new BigDecimal("20000"), 20L, "cached", 5L
			);
			productCacheManager.put(key, cached, Duration.ofMinutes(10));
			AtomicInteger loaderCallCount = new AtomicInteger(0);

			// Act
			ProductDetailOutDto result = productCacheManager.getOrLoad(
				key, ProductDetailOutDto.class, Duration.ofMinutes(10),
				() -> {
					loaderCallCount.incrementAndGet();
					return new ProductDetailOutDto(
						99L, 99L, "New", "New",
						new BigDecimal("99999"), 99L, "new", 99L
					);
				}
			);

			// Assert — loader 미호출, 캐시된 값 반환
			assertAll(
				() -> assertThat(loaderCallCount.get()).isEqualTo(0),
				() -> assertThat(result.id()).isEqualTo(2L),
				() -> assertThat(result.name()).isEqualTo("CachedProduct")
			);
		}

	}


	@Nested
	@DisplayName("refreshProductDetail()")
	class RefreshProductDetailTest {

		@Test
		@DisplayName("[refreshProductDetail()] Supplier가 ProductCacheDto 반환 -> product:v1:{id} 키에 캐시 저장")
		void refreshProductDetailSuccess() {

			// Arrange
			ProductCacheDto dto = new ProductCacheDto(
				1L, 1L, "TestBrand", "TestProduct",
				new BigDecimal("10000"), 100L, "desc", 5L
			);

			// Act
			productCacheManager.refreshProductDetail(1L, () -> dto);

			// Assert — 상세 캐시 키로 저장 확인
			Optional<ProductCacheDto> result = productCacheManager.get(DETAIL_KEY_PREFIX + 1, ProductCacheDto.class);
			assertAll(
				() -> assertThat(result).isPresent(),
				() -> assertThat(result.get().id()).isEqualTo(1L),
				() -> assertThat(result.get().brandName()).isEqualTo("TestBrand"),
				() -> assertThat(result.get().name()).isEqualTo("TestProduct"),
				() -> assertThat(result.get().likeCount()).isEqualTo(5L)
			);
		}


		@Test
		@DisplayName("[refreshProductDetail()] Supplier가 null 반환 -> 캐시 저장하지 않음")
		void refreshProductDetailNullSkipped() {

			// Act
			productCacheManager.refreshProductDetail(999L, () -> null);

			// Assert — 캐시에 저장되지 않음
			Optional<ProductCacheDto> result = productCacheManager.get(DETAIL_KEY_PREFIX + 999, ProductCacheDto.class);
			assertThat(result).isEmpty();
		}

	}


	@Nested
	@DisplayName("refreshIdList()")
	class RefreshIdListTest {

		@Test
		@DisplayName("[refreshIdList()] Supplier가 IdListCacheEntry 반환 -> 지정된 키에 캐시 저장")
		void refreshIdListSuccess() {

			// Arrange
			String cacheKey = "products:ids:v1:all:LATEST:0:20";
			IdListCacheEntry entry = new IdListCacheEntry(List.of(1L, 2L, 3L), 3);

			// Act
			productCacheManager.refreshIdList(cacheKey, () -> entry);

			// Assert
			Optional<IdListCacheEntry> result = productCacheManager.get(cacheKey, IdListCacheEntry.class);
			assertAll(
				() -> assertThat(result).isPresent(),
				() -> assertThat(result.get().ids()).containsExactly(1L, 2L, 3L),
				() -> assertThat(result.get().totalElements()).isEqualTo(3)
			);
		}


		@Test
		@DisplayName("[refreshIdList()] Supplier가 null 반환 -> 캐시 저장하지 않음")
		void refreshIdListNullSkipped() {

			// Arrange
			String cacheKey = "products:ids:v1:all:LATEST:0:20";

			// Act
			productCacheManager.refreshIdList(cacheKey, () -> null);

			// Assert
			Optional<IdListCacheEntry> result = productCacheManager.get(cacheKey, IdListCacheEntry.class);
			assertThat(result).isEmpty();
		}


		@Test
		@DisplayName("[refreshIdList()] 기존 캐시 존재 시 -> 덮어쓰기. 최신 데이터로 갱신")
		void refreshIdListOverwrite() {

			// Arrange — 기존 캐시 저장
			String cacheKey = "products:ids:v1:1:PRICE_ASC:0:20";
			productCacheManager.put(cacheKey, new IdListCacheEntry(List.of(1L), 1), Duration.ofMinutes(5));

			// Act — 새 데이터로 갱신
			IdListCacheEntry updated = new IdListCacheEntry(List.of(1L, 2L), 2);
			productCacheManager.refreshIdList(cacheKey, () -> updated);

			// Assert — 최신 데이터로 갱신 확인
			Optional<IdListCacheEntry> result = productCacheManager.get(cacheKey, IdListCacheEntry.class);
			assertAll(
				() -> assertThat(result).isPresent(),
				() -> assertThat(result.get().ids()).containsExactly(1L, 2L),
				() -> assertThat(result.get().totalElements()).isEqualTo(2)
			);
		}

	}


	@Nested
	@DisplayName("deleteProductDetail()")
	class DeleteProductDetailTest {

		@Test
		@DisplayName("[deleteProductDetail()] 상품 상세 캐시 삭제 -> product:v1:{id} 키 삭제 확인")
		void deleteProductDetailSuccess() {

			// Arrange — 상세 캐시 저장
			ProductCacheDto dto = new ProductCacheDto(
				1L, 1L, "Brand", "Product",
				new BigDecimal("10000"), 100L, "desc", 0L
			);
			productCacheManager.put(DETAIL_KEY_PREFIX + 1, dto, Duration.ofMinutes(10));

			// Act
			productCacheManager.deleteProductDetail(1L);

			// Assert — 캐시에서 삭제됨
			Optional<ProductCacheDto> result = productCacheManager.get(DETAIL_KEY_PREFIX + 1, ProductCacheDto.class);
			assertThat(result).isEmpty();
		}


		@Test
		@DisplayName("[deleteProductDetail()] 존재하지 않는 키 삭제 -> 예외 없이 정상 처리")
		void deleteProductDetailNonExistent() {

			// Act & Assert — 예외 없음
			productCacheManager.deleteProductDetail(999L);
			Optional<ProductCacheDto> result = productCacheManager.get(DETAIL_KEY_PREFIX + 999, ProductCacheDto.class);
			assertThat(result).isEmpty();
		}

	}


	@Nested
	@DisplayName("mgetProductDetails()")
	class MgetProductDetailsTest {

		@Test
		@DisplayName("[mgetProductDetails()] 전체 히트 -> 모든 ID에 대한 ProductCacheDto 반환. ID 순서 보존")
		void mgetAllHit() {

			// Arrange — 3건 캐시 저장
			for (long i = 1; i <= 3; i++) {
				ProductCacheDto dto = new ProductCacheDto(
					i, 1L, "Brand", "Product" + i,
					new BigDecimal("10000"), 100L, null, 0L
				);
				productCacheManager.put(DETAIL_KEY_PREFIX + i, dto, Duration.ofMinutes(10));
			}

			// Act
			List<ProductCacheDto> result = productCacheManager.mgetProductDetails(List.of(1L, 2L, 3L));

			// Assert — 순서 보존, 모두 non-null
			assertAll(
				() -> assertThat(result).hasSize(3),
				() -> assertThat(result.get(0).id()).isEqualTo(1L),
				() -> assertThat(result.get(0).name()).isEqualTo("Product1"),
				() -> assertThat(result.get(1).id()).isEqualTo(2L),
				() -> assertThat(result.get(2).id()).isEqualTo(3L)
			);
		}


		@Test
		@DisplayName("[mgetProductDetails()] partial miss -> 히트된 항목은 반환, miss된 항목은 null. 위치 보존")
		void mgetPartialMiss() {

			// Arrange — id=1만 캐시, id=2는 미캐시
			ProductCacheDto dto1 = new ProductCacheDto(
				1L, 1L, "Brand", "Product1",
				new BigDecimal("10000"), 100L, null, 0L
			);
			productCacheManager.put(DETAIL_KEY_PREFIX + 1, dto1, Duration.ofMinutes(10));

			// Act
			List<ProductCacheDto> result = productCacheManager.mgetProductDetails(List.of(1L, 2L));

			// Assert — id=1 히트, id=2 null
			assertAll(
				() -> assertThat(result).hasSize(2),
				() -> assertThat(result.get(0)).isNotNull(),
				() -> assertThat(result.get(0).id()).isEqualTo(1L),
				() -> assertThat(result.get(1)).isNull()
			);
		}


		@Test
		@DisplayName("[mgetProductDetails()] 전체 miss -> 모든 항목이 null인 리스트 반환")
		void mgetAllMiss() {

			// Act
			List<ProductCacheDto> result = productCacheManager.mgetProductDetails(List.of(100L, 200L));

			// Assert
			assertAll(
				() -> assertThat(result).hasSize(2),
				() -> assertThat(result.get(0)).isNull(),
				() -> assertThat(result.get(1)).isNull()
			);
		}


		@Test
		@DisplayName("[mgetProductDetails()] 빈 ID 목록 -> 빈 리스트 반환")
		void mgetEmptyIds() {

			// Act
			List<ProductCacheDto> result = productCacheManager.mgetProductDetails(List.of());

			// Assert
			assertThat(result).isEmpty();
		}

	}

}
