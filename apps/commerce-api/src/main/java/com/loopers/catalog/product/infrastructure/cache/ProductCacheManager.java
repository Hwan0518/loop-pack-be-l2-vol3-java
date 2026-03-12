package com.loopers.catalog.product.infrastructure.cache;


import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.config.redis.RedisConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static com.loopers.catalog.product.infrastructure.cache.ProductCacheConstants.DETAIL_KEY_PREFIX;
import static com.loopers.catalog.product.infrastructure.cache.ProductCacheConstants.DETAIL_TTL;
import static com.loopers.catalog.product.infrastructure.cache.ProductCacheConstants.ID_LIST_TTL;


/**
 * 상품 캐시 관리자
 * - Redis 기반 Cache-Aside 패턴 지원
 * - 모든 메서드는 Redis 장애 시 예외를 격리하고 로깅만 수행
 * - 읽기: replica-preferred, 쓰기/삭제: master
 * - 캐시 스탬피드 보호: CacheLock + PER (Probabilistic Early Refresh)
 *
 * 1. get(key, Class) — 단순 타입 캐시 조회
 * 2. get(key, TypeReference) — 제네릭 타입 캐시 조회
 * 3. put(key, value, ttl) — 캐시 저장 (TTL jitter 포함)
 * 4. evict(key) — 단일 키 삭제
 * 5. getOrLoad(key, type, ttl, loader) — Cache-Aside + 스탬피드 보호
 * 6. getOrLoadWithPer(key, type, ttl, loader) — getOrLoad + PER (TTL 임박 시 확률적 갱신)
 * 7. refreshProductDetail(productId, loader) — 상품 상세 캐시 write-through
 * 8. refreshIdList(cacheKey, loader) — ID 리스트 캐시 write-through (단건)
 * 9. deleteProductDetail(productId) — 상품 상세 캐시 삭제
 * 10. mgetProductDetails(productIds) — 여러 상품 상세 일괄 조회 (MGET)
 */
@Slf4j
@Component
public class ProductCacheManager {

	// redis
	private final RedisTemplate<String, String> readTemplate;
	private final RedisTemplate<String, String> writeTemplate;
	// util
	private final ObjectMapper objectMapper;
	// cache
	private final CacheLock cacheLock;
	// PER 비동기 갱신 전용 스레드 풀 (ForkJoinPool 고갈 방지)
	private final ExecutorService perExecutor = Executors.newFixedThreadPool(3);


	public ProductCacheManager(
		RedisTemplate<String, String> readTemplate,
		@Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> writeTemplate,
		ObjectMapper objectMapper,
		CacheLock cacheLock
	) {
		this.readTemplate = readTemplate;
		this.writeTemplate = writeTemplate;
		this.objectMapper = objectMapper;
		this.cacheLock = cacheLock;
	}


	// 1. 단순 타입 캐시 조회
	public <T> Optional<T> get(String key, Class<T> type) {

		try {
			// replica에서 JSON 문자열 조회
			String json = readTemplate.opsForValue().get(key);
			if (json == null) {
				return Optional.empty();
			}

			// JSON → 객체 역직렬화
			return Optional.of(objectMapper.readValue(json, type));
		} catch (Exception e) {
			log.warn("캐시 조회 실패. key={}", key, e);
			return Optional.empty();
		}
	}


	// 2. 제네릭 타입 캐시 조회
	public <T> Optional<T> get(String key, TypeReference<T> typeRef) {

		try {
			// replica에서 JSON 문자열 조회
			String json = readTemplate.opsForValue().get(key);
			if (json == null) {
				return Optional.empty();
			}

			// JSON → 제네릭 타입 역직렬화
			return Optional.of(objectMapper.readValue(json, typeRef));
		} catch (Exception e) {
			log.warn("캐시 조회 실패. key={}", key, e);
			return Optional.empty();
		}
	}


	// 3. 캐시 저장 (TTL jitter 포함)
	public void put(String key, Object value, Duration ttl) {

		try {
			// 객체 → JSON 직렬화
			String json = objectMapper.writeValueAsString(value);

			// jitter 적용된 TTL로 master에 저장
			Duration jitteredTtl = applyJitter(ttl);
			writeTemplate.opsForValue().set(key, json, jitteredTtl);
		} catch (Exception e) {
			log.warn("캐시 저장 실패. key={}", key, e);
		}
	}


	// 4. 단일 키 삭제
	public void evict(String key) {

		try {
			writeTemplate.delete(key);
		} catch (Exception e) {
			log.warn("캐시 삭제 실패. key={}", key, e);
		}
	}


	// 5. Cache-Aside + 스탬피드 보호 (CacheLock + double-check)
	public <T> T getOrLoad(String key, Class<T> type, Duration ttl, Supplier<T> loader) {

		// 캐시 조회
		Optional<T> cached = get(key, type);
		if (cached.isPresent()) {
			return cached.get();
		}

		// 캐시 미스 → 락 획득 후 DB 조회 (1회만)
		return cacheLock.executeWithLock(key, () -> {

			// double-check (대기 중 다른 스레드가 캐시 저장했을 수 있음)
			Optional<T> doubleCheck = get(key, type);
			if (doubleCheck.isPresent()) {
				return doubleCheck.get();
			}

			// DB 조회 + 캐시 저장 (null이면 캐시에 저장하지 않음)
			T value = loader.get();
			if (value != null) {
				put(key, value, ttl);
			}
			return value;
		});
	}


	// 6. Cache-Aside + PER (Probabilistic Early Refresh) + 스탬피드 보호
	public <T> T getOrLoadWithPer(String key, Class<T> type, Duration ttl, Supplier<T> loader) {

		// 캐시 조회
		Optional<T> cached = get(key, type);
		if (cached.isPresent()) {

			// PER: TTL 잔여 시간 확인 → 임박 시 확률적 비동기 갱신
			if (shouldEarlyRefresh(key, ttl)) {
				CompletableFuture.runAsync(() -> {
					try {
						T fresh = loader.get();
						put(key, fresh, ttl);
					} catch (Exception e) {
						log.warn("PER 비동기 갱신 실패. key={}", key, e);
					}
				}, perExecutor);
			}

			return cached.get();
		}

		// 캐시 미스 → 락 + double-check
		return cacheLock.executeWithLock(key, () -> {

			// double-check (대기 중 다른 스레드가 캐시 저장했을 수 있음)
			Optional<T> doubleCheck = get(key, type);
			if (doubleCheck.isPresent()) {
				return doubleCheck.get();
			}

			// DB 조회 + 캐시 저장 (null이면 캐시에 저장하지 않음)
			T value = loader.get();
			if (value != null) {
				put(key, value, ttl);
			}
			return value;
		});
	}


	// 7. 상품 상세 캐시 write-through (Supplier 기반)
	public void refreshProductDetail(Long productId, Supplier<ProductCacheDto> loader) {

		try {
			// Read Model에서 ProductCacheDto 로드
			ProductCacheDto dto = loader.get();
			if (dto == null) {
				return;
			}

			// 상세 캐시에 저장
			put(DETAIL_KEY_PREFIX + productId, dto, DETAIL_TTL);
		} catch (Exception e) {
			log.warn("상품 상세 캐시 write-through 실패. productId={}", productId, e);
		}
	}


	// 8. ID 리스트 캐시 write-through (단건, Supplier 기반)
	public void refreshIdList(String cacheKey, Supplier<IdListCacheEntry> loader) {

		try {
			// DB에서 해당 조건의 ID 리스트 재조회
			IdListCacheEntry entry = loader.get();
			if (entry == null) {
				return;
			}

			// ID 리스트 캐시에 저장
			put(cacheKey, entry, ID_LIST_TTL);
		} catch (Exception e) {
			log.warn("ID 리스트 캐시 write-through 실패. key={}", cacheKey, e);
		}
	}


	// 9. 상품 상세 캐시 삭제 (상품 삭제 시 예외적 사용)
	public void deleteProductDetail(Long productId) {
		evict(DETAIL_KEY_PREFIX + productId);
	}


	// 10. 여러 상품 상세 일괄 조회 (MGET)
	public List<ProductCacheDto> mgetProductDetails(List<Long> productIds) {

		try {
			// 상세 캐시 키 목록 생성
			List<String> keys = productIds.stream()
				.map(id -> DETAIL_KEY_PREFIX + id)
				.toList();

			// MGET으로 일괄 조회 (null 포함 리스트 반환)
			List<String> jsonValues = readTemplate.opsForValue().multiGet(keys);
			if (jsonValues == null) {
				return productIds.stream().map(id -> (ProductCacheDto) null).toList();
			}

			// JSON → ProductCacheDto 역직렬화 (실패 시 null)
			return jsonValues.stream()
				.map(json -> {
					if (json == null) {
						return null;
					}
					try {
						return objectMapper.readValue(json, ProductCacheDto.class);
					} catch (Exception e) {
						log.warn("MGET 역직렬화 실패", e);
						return null;
					}
				})
				.toList();
		} catch (Exception e) {
			log.warn("MGET 캐시 조회 실패. productIds={}", productIds, e);
			// 전체 실패 시 null 리스트 반환 (partial miss 처리로 fallback)
			return productIds.stream().map(id -> (ProductCacheDto) null).toList();
		}
	}


	/**
	 * private method
	 * - PER 판정: TTL의 마지막 20% 구간에서 확률적 갱신
	 * - TTL jitter 적용: base TTL에 0~10% 랜덤 추가
	 */

	// PER 판정: TTL의 마지막 20% 구간에서 확률적 갱신
	private boolean shouldEarlyRefresh(String key, Duration baseTtl) {

		try {
			// 남은 TTL 조회 (밀리초)
			Long remainMs = readTemplate.getExpire(key, TimeUnit.MILLISECONDS);
			if (remainMs == null || remainMs <= 0) {
				return false;
			}

			// threshold = base TTL의 20%
			long thresholdMs = baseTtl.toMillis() / 5;
			if (remainMs > thresholdMs) {
				return false;
			}

			// 남은 시간이 적을수록 갱신 확률 증가 (선형)
			double probability = 1.0 - ((double) remainMs / thresholdMs);
			return ThreadLocalRandom.current().nextDouble() < probability;
		} catch (Exception e) {
			return false;
		}
	}


	// TTL jitter 적용: base TTL + random(0, base TTL * 0.1)
	Duration applyJitter(Duration baseTtl) {

		long baseMs = baseTtl.toMillis();
		long jitterBound = baseMs / 10;

		// jitter 범위가 0 이하이면 원래 TTL 반환
		if (jitterBound <= 0) {
			return baseTtl;
		}

		long jitterMs = ThreadLocalRandom.current().nextLong(jitterBound + 1);
		return Duration.ofMillis(baseMs + jitterMs);
	}

}
