package com.loopers.catalog.product.infrastructure.cache;


import com.loopers.catalog.product.domain.model.enums.ProductSortType;

import java.time.Duration;


/**
 * 상품 캐시 상수
 * - 캐시 키 접두사, 버전, 페이지 기본값, TTL 정의
 * - 캐시 키 스키마 버전(v1)으로 배포 안전성 확보
 * - 캐시 키 생성 유틸리티 (중복 방지)
 */
public final class ProductCacheConstants {

	// 캐시 키 버전
	public static final String CACHE_VERSION = "v1";

	// 상세 캐시 키 접두사 — product:v1:{productId}
	public static final String DETAIL_KEY_PREFIX = "product:" + CACHE_VERSION + ":";

	// ID 리스트 캐시 키 접두사 — products:ids:v1:{brand|all}:{sort}:{page}:{size}
	public static final String ID_LIST_KEY_PREFIX = "products:ids:" + CACHE_VERSION + ":";

	// 캐시 적용 기본 페이지 크기
	public static final int DEFAULT_PAGE_SIZE = 20;

	// 캐시 적용 최대 페이지 (0-based, page 0 ~ 1)
	public static final int MAX_CACHEABLE_PAGE = 2;

	// ID 리스트 TTL (3분)
	public static final Duration ID_LIST_TTL = Duration.ofMinutes(3);

	// 상세 캐시 TTL (2분)
	public static final Duration DETAIL_TTL = Duration.ofMinutes(2);


	// ID 리스트 캐시 키 생성: products:ids:v1:{brandId|all}:{sortType|LATEST}:{page}:{size}
	public static String buildIdListCacheKey(Long brandId, ProductSortType sortType, int page, int size) {
		String brandPart = brandId != null ? brandId.toString() : "all";
		String sortPart = sortType != null ? sortType.name() : "LATEST";
		return ID_LIST_KEY_PREFIX + brandPart + ":" + sortPart + ":" + page + ":" + size;
	}


	private ProductCacheConstants() {
	}

}
