package com.loopers.catalog.product.application.service;


import com.loopers.catalog.product.application.dto.out.*;
import com.loopers.support.common.event.catalog.ProductViewedPayload;
import com.loopers.catalog.product.application.port.out.query.ProductQueryPort;
import com.loopers.catalog.product.application.port.out.query.criteria.ProductSearchCriteria;
import com.loopers.catalog.product.domain.model.Product;
import com.loopers.catalog.product.domain.model.enums.ProductSortType;
import com.loopers.catalog.product.domain.repository.ProductQueryRepository;
import com.loopers.catalog.product.domain.repository.ProductReadModelRepository;
import com.loopers.catalog.product.domain.repository.vo.PageCriteria;
import com.loopers.catalog.product.domain.repository.vo.PageResult;
import com.loopers.catalog.product.application.port.out.cache.ProductCachePort;
import com.loopers.catalog.product.application.port.out.cache.dto.IdListCacheEntry;
import com.loopers.catalog.product.application.port.out.cache.dto.ProductCacheDto;
import com.loopers.support.common.event.EventType;
import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import com.loopers.support.common.outbox.application.port.OutboxEventPort;
import com.loopers.support.common.outbox.application.util.JsonSerializer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.loopers.catalog.product.application.port.out.cache.ProductCacheConstants.*;


@Service
@RequiredArgsConstructor
public class ProductQueryService {

	// repository
	private final ProductQueryRepository productQueryRepository;
	private final ProductReadModelRepository productReadModelRepository;
	// port
	private final ProductQueryPort productQueryPort;
	// outbox
	private final OutboxEventPort outboxEventPort;
	private final JsonSerializer jsonSerializer;
	// cache
	private final ProductCachePort productCacheManager;


	/**
	 * 상품 조회 서비스
	 * 1. ID로 활성 상품 조회
	 * 2. 브랜드의 활성 상품 존재 여부 확인
	 * 3. ID로 상품 조회 (삭제 포함, 관리자용)
	 * 4. 사용자 상품 목록 검색 (2계층 캐시: ID 리스트 → MGET 상세)
	 * 5. 관리자 상품 목록 검색 (캐시 미적용)
	 * 6. ID 목록으로 활성 상품 일괄 조회 (Cross-BC 전용)
	 * 7. 상품 상세 캐시 조회 (PER + 스탬피드 보호)
	 * 8. 브랜드 ID로 활성 상품 ID 목록 조회 (브랜드명 write-through용)
	 * 9. 관리자 상품 상세 조회 (Read Model projection — 삭제된 브랜드에도 안전)
	 * 10. VIEW Outbox 저장 (D7: 같은 TX에서 조회 + outbox INSERT)
	 */

	// 1. ID로 활성 상품 조회
	@Transactional(readOnly = true)
	public Product findActiveById(Long id) {
		return productQueryRepository.findActiveById(id)
			.orElseThrow(() -> new CoreException(ErrorType.PRODUCT_NOT_FOUND));
	}


	// 2. 브랜드의 활성 상품 존재 여부 확인
	@Transactional(readOnly = true)
	public boolean existsActiveByBrandId(Long brandId) {
		return productQueryRepository.existsActiveByBrandId(brandId);
	}


	// 3. ID로 상품 조회 (삭제 포함, 관리자용)
	@Transactional(readOnly = true)
	public Product findById(Long id) {
		return productQueryRepository.findById(id)
			.orElseThrow(() -> new CoreException(ErrorType.PRODUCT_NOT_FOUND));
	}


	// 4. 사용자 상품 목록 검색 (2계층 캐시: ID 리스트 → MGET 상세)
	@Transactional(readOnly = true)
	public ProductPageOutDto searchProducts(Long brandId, ProductSortType sortType, int page, int size) {

		// 캐시 적용 조건 확인 (page <= MAX_CACHEABLE_PAGE && size == DEFAULT_PAGE_SIZE)
		if (!isCacheable(page, size)) {
			return searchFromDb(brandId, sortType, page, size);
		}

		// Layer 1: ID 리스트 캐시 조회 (cache-aside + 스탬피드 보호)
		String idListKey = buildIdListCacheKey(brandId, sortType, page, size);
		IdListCacheEntry idList = productCacheManager.getOrLoad(
			idListKey, IdListCacheEntry.class, ID_LIST_TTL,
			() -> loadIdListFromDb(brandId, sortType, page, size)
		);

		// 빈 결과인 경우 빈 페이지 반환
		if (idList.ids().isEmpty()) {
			return new ProductPageOutDto(List.of(), page, size, idList.totalElements());
		}

		// Layer 2: MGET 상세 캐시 조회
		List<ProductCacheDto> cached = productCacheManager.mgetProductDetails(idList.ids());

		// partial miss 처리 — miss된 ID를 DB에서 조회 후 캐시 저장
		List<Long> missedIds = extractMissedIds(idList.ids(), cached);
		if (!missedIds.isEmpty()) {
			List<ProductCacheDto> fromDb = loadAndCacheDetails(missedIds);
			cached = mergeInOrder(idList.ids(), cached, fromDb);
		}

		// dangling ID 방어 (삭제되었으나 ID 리스트에 남은 경우 null skip)
		List<ProductOutDto> content = cached.stream()
			.filter(Objects::nonNull)
			.map(ProductCacheDto::toProductOutDto)
			.toList();

		return new ProductPageOutDto(content, page, size, idList.totalElements());
	}


	// 5. 관리자 상품 목록 검색 (캐시 미적용 — 관리자는 실시간 데이터 필요)
	@Transactional(readOnly = true)
	public AdminProductPageOutDto searchAdminProducts(Long brandId, ProductSortType sortType, int page, int size) {

		// 검색 조건 생성
		ProductSearchCriteria criteria = new ProductSearchCriteria(brandId, sortType);

		// QueryPort를 통한 검색
		PageResult<AdminProductOutDto> result = productQueryPort.searchAdminProducts(criteria, new PageCriteria(page, size));

		// DTO 변환
		return AdminProductPageOutDto.from(result);
	}


	// 6. ID 목록으로 활성 상품 일괄 조회 (Cross-BC 전용)
	@Transactional(readOnly = true)
	public List<Product> findActiveByIds(List<Long> ids) {
		return productQueryRepository.findActiveByIds(ids);
	}


	// 7. 상품 상세 캐시 조회 (PER + 스탬피드 보호, Read Model projection 기반)
	@Transactional(readOnly = true)
	public ProductDetailOutDto getOrLoadProductDetail(Long productId) {

		// 캐시 키: product:v1:{productId}
		String cacheKey = DETAIL_KEY_PREFIX + productId;

		// PER + 스탬피드 보호로 캐시 조회/로드 (ProductCacheDto 기반)
		ProductCacheDto cacheDto = productCacheManager.getOrLoadWithPer(
			cacheKey, ProductCacheDto.class, DETAIL_TTL,
			() -> productQueryPort.findProductCacheDtoById(productId)
		);

		// 상품이 존재하지 않거나 삭제된 경우 (loader가 null 반환)
		if (cacheDto == null) {
			throw new CoreException(ErrorType.PRODUCT_NOT_FOUND);
		}

		// ProductCacheDto → ProductDetailOutDto 변환
		return cacheDto.toProductDetailOutDto();
	}


	// 8. 브랜드 ID로 활성 상품 ID 목록 조회 (브랜드명 write-through용)
	@Transactional(readOnly = true)
	public List<Long> findActiveIdsByBrandId(Long brandId) {
		return productReadModelRepository.findActiveIdsByBrandId(brandId);
	}


	// 9. 관리자 상품 상세 조회 (Read Model projection — 삭제된 브랜드에도 비정규화된 brandName 사용)
	@Transactional(readOnly = true)
	public AdminProductDetailOutDto getAdminProductDetail(Long productId) {
		AdminProductDetailOutDto result = productQueryPort.findAdminProductDetailById(productId);
		if (result == null) {
			throw new CoreException(ErrorType.PRODUCT_NOT_FOUND);
		}
		return result;
	}


	/**
	 * private method
	 * - 캐시 적용 조건 판정
	 * - DB 직접 조회 (캐시 미적용 경로)
	 * - ID 리스트 DB 조회 (cache-aside loader)
	 * - MGET partial miss 처리
	 * - 캐시 키 생성
	 */

	// 캐시 적용 조건 판정
	private boolean isCacheable(int page, int size) {
		return page < MAX_CACHEABLE_PAGE && size == DEFAULT_PAGE_SIZE;
	}


	// DB 직접 조회 (캐시 미적용 경로 — page 3 이상 또는 size != DEFAULT_PAGE_SIZE)
	private ProductPageOutDto searchFromDb(Long brandId, ProductSortType sortType, int page, int size) {
		ProductSearchCriteria criteria = new ProductSearchCriteria(brandId, sortType);
		PageResult<ProductOutDto> result = productQueryPort.searchProducts(criteria, new PageCriteria(page, size));
		return ProductPageOutDto.from(result);
	}


	// ID 리스트 DB 조회 (cache-aside loader)
	private IdListCacheEntry loadIdListFromDb(Long brandId, ProductSortType sortType, int page, int size) {
		ProductSearchCriteria criteria = new ProductSearchCriteria(brandId, sortType);
		return productQueryPort.searchProductIds(criteria, new PageCriteria(page, size));
	}


	// MGET partial miss된 ID 추출
	private List<Long> extractMissedIds(List<Long> ids, List<ProductCacheDto> cached) {
		List<Long> missed = new ArrayList<>();
		for (int i = 0; i < ids.size(); i++) {
			if (i >= cached.size() || cached.get(i) == null) {
				missed.add(ids.get(i));
			}
		}
		return missed;
	}


	// miss된 상품을 DB에서 조회 후 개별 캐시 저장
	private List<ProductCacheDto> loadAndCacheDetails(List<Long> missedIds) {
		List<ProductCacheDto> dtos = productQueryPort.findProductCacheDtosByIds(missedIds);
		for (ProductCacheDto dto : dtos) {
			productCacheManager.put(DETAIL_KEY_PREFIX + dto.id(), dto, DETAIL_TTL);
		}
		return dtos;
	}


	// ID 순서대로 cached와 fromDb를 병합 (ID 리스트 순서 보존)
	private List<ProductCacheDto> mergeInOrder(List<Long> ids, List<ProductCacheDto> cached, List<ProductCacheDto> fromDb) {

		// fromDb를 id 기반 Map으로 변환
		Map<Long, ProductCacheDto> dbMap = fromDb.stream()
			.collect(Collectors.toMap(ProductCacheDto::id, Function.identity()));

		// ID 순서대로 병합 — cached에 있으면 cached 사용, 없으면 dbMap에서 조회
		List<ProductCacheDto> merged = new ArrayList<>(ids.size());
		for (int i = 0; i < ids.size(); i++) {
			ProductCacheDto c = (i < cached.size()) ? cached.get(i) : null;
			if (c != null) {
				merged.add(c);
			} else {
				merged.add(dbMap.get(ids.get(i)));
			}
		}
		return merged;
	}


	// 10. VIEW Outbox 저장 (D7: 같은 TX에서 조회 + outbox INSERT)
	@Transactional
	public void saveViewOutbox(Long userId, Long productId) {
		outboxEventPort.save(EventType.PRODUCT_VIEWED, String.valueOf(productId),
			String.valueOf(productId),
			jsonSerializer.toJson(ProductViewedPayload.of(userId, productId)));
	}

}
