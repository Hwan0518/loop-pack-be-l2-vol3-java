package com.loopers.catalog.product.application.service;


import com.loopers.catalog.product.application.dto.in.AdminProductCreateInDto;
import com.loopers.catalog.product.application.dto.in.AdminProductUpdateInDto;
import com.loopers.catalog.product.application.port.out.client.cart.CartItemCleanupManager;
import com.loopers.catalog.product.application.port.out.client.engagement.ProductLikeCleanupManager;
import com.loopers.catalog.product.application.port.out.query.ProductQueryPort;
import com.loopers.catalog.product.application.port.out.query.criteria.ProductSearchCriteria;
import com.loopers.catalog.product.domain.model.Product;
import com.loopers.catalog.product.domain.model.enums.ProductSortType;
import com.loopers.catalog.product.domain.repository.ProductCommandRepository;
import com.loopers.catalog.product.domain.repository.ProductQueryRepository;
import com.loopers.catalog.product.domain.repository.ProductReadModelRepository;
import com.loopers.catalog.product.domain.repository.vo.PageCriteria;
import com.loopers.catalog.product.infrastructure.cache.ProductCacheManager;
import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.loopers.catalog.product.infrastructure.cache.ProductCacheConstants.DEFAULT_PAGE_SIZE;
import static com.loopers.catalog.product.infrastructure.cache.ProductCacheConstants.MAX_CACHEABLE_PAGE;
import static com.loopers.catalog.product.infrastructure.cache.ProductCacheConstants.buildIdListCacheKey;


@Service
public class ProductCommandService {

	// repository
	private final ProductCommandRepository productCommandRepository;
	private final ProductQueryRepository productQueryRepository;
	private final ProductReadModelRepository readModelRepository;
	// cache
	private final ProductCacheManager productCacheManager;
	// port
	private final ProductQueryPort productQueryPort;
	// port (@Lazy: Cross-BC 순환 의존 방지 — ProductCommandService ↔ ProductLikeCommandService 간 ACL 경유 순환)
	private final ProductLikeCleanupManager productLikeCleanupManager;
	private final CartItemCleanupManager cartItemCleanupManager;

	public ProductCommandService(
		ProductCommandRepository productCommandRepository,
		ProductQueryRepository productQueryRepository,
		ProductReadModelRepository readModelRepository,
		ProductCacheManager productCacheManager,
		ProductQueryPort productQueryPort,
		@Lazy ProductLikeCleanupManager productLikeCleanupManager,
		@Lazy CartItemCleanupManager cartItemCleanupManager
	) {
		this.productCommandRepository = productCommandRepository;
		this.productQueryRepository = productQueryRepository;
		this.readModelRepository = readModelRepository;
		this.productCacheManager = productCacheManager;
		this.productQueryPort = productQueryPort;
		this.productLikeCleanupManager = productLikeCleanupManager;
		this.cartItemCleanupManager = cartItemCleanupManager;
	}


	/**
	 * 상품 명령 서비스
	 * 1. 상품 생성
	 * 2. 상품 수정
	 * 3. 상품 삭제
	 * 4. 좋아요 수 증가 (Read Model 원자적 카운터 + 상세 캐시 write-through)
	 * 5. 좋아요 수 감소 (Read Model 원자적 카운터 + 상세 캐시 write-through)
	 * 6. 상품 재고 차감 (비관적 쓰기 락 + 상세 캐시 write-through)
	 * 7. 상품 재고 증가 (비관적 쓰기 락 + 상세 캐시 write-through — 보상 트랜잭션)
	 * 8. 상품 좋아요 전체 삭제
	 * 9. 장바구니 항목 전체 삭제
	 * 10. Read Model 동기화 (상품 생성/수정 시 Facade에서 호출)
	 * 11. Read Model 브랜드명 일괄 동기화 (브랜드 수정 시 호출)
	 * 12. 상품 상세 캐시 write-through (Facade에서 호출)
	 * 13. 상품 상세 캐시 삭제 (상품 삭제 시 Facade에서 호출)
	 * 14. ID 리스트 캐시 write-through — 모든 정렬 (Facade에서 호출)
	 * 15. ID 리스트 캐시 write-through — 특정 정렬 (Facade에서 호출)
	 */

	// 1. 상품 생성
	@Transactional
	public Product createProduct(AdminProductCreateInDto inDto) {

		// 상품 생성
		Product product = Product.create(
			inDto.brandId(),
			inDto.name(),
			inDto.price(),
			inDto.stock(),
			inDto.description()
		);

		// 저장
		return productCommandRepository.save(product);
	}


	// 2. 상품 수정
	@Transactional
	public Product updateProduct(Product product, AdminProductUpdateInDto inDto) {

		// 상품 정보 변경
		product.changeName(inDto.name());
		product.changePrice(inDto.price());
		product.changeStock(inDto.stock());
		product.changeDescription(inDto.description());

		// 저장
		return productCommandRepository.save(product);
	}


	// 3. 상품 삭제
	@Transactional
	public void deleteProduct(Product product) {

		// 삭제 (도메인 로직)
		product.delete();

		// 삭제 저장
		productCommandRepository.delete(product);

		// Read Model soft delete (관리자 목록 조회에서 삭제 상품도 조회 가능하도록)
		readModelRepository.softDelete(product.getId());
	}


	// 4. 좋아요 수 증가 (Read Model 원자적 카운터 + 상세 캐시 write-through)
	@Transactional
	public void increaseLikeCount(Long productId) {

		// Read Model 좋아요 수 증가 (likes 테이블이 SoT, Read Model이 유일한 projection)
		readModelRepository.increaseLikeCount(productId);

		// 상세 캐시 write-through (ID 리스트는 TTL 자연 만료 — 고빈도 트리거 최적화)
		productCacheManager.refreshProductDetail(productId, () -> productQueryPort.findProductCacheDtoById(productId));
	}


	// 5. 좋아요 수 감소 (Read Model 원자적 카운터 + 상세 캐시 write-through)
	@Transactional
	public void decreaseLikeCount(Long productId) {

		// Read Model 좋아요 수 감소 (likes 테이블이 SoT, Read Model이 유일한 projection)
		readModelRepository.decreaseLikeCount(productId);

		// 상세 캐시 write-through (ID 리스트는 TTL 자연 만료 — 고빈도 트리거 최적화)
		productCacheManager.refreshProductDetail(productId, () -> productQueryPort.findProductCacheDtoById(productId));
	}


	// 6. 상품 재고 차감 (비관적 쓰기 락 + 상세 캐시 write-through)
	@Transactional
	public void decreaseStock(Long productId, Long quantity) {

		// 활성 상품 조회 (비관적 쓰기 락 — 동시 재고 차감 경합 방지)
		Product product = productQueryRepository.findActiveByIdForUpdate(productId)
			.orElseThrow(() -> new CoreException(ErrorType.PRODUCT_NOT_FOUND));

		// 재고 차감 (도메인 로직 — 재고 부족 시 PRODUCT_OUT_OF_STOCK 예외)
		product.decreaseStock(quantity);

		// 재고가 차감된 상품 저장
		productCommandRepository.save(product);

		// Read Model 재고 동기화
		readModelRepository.updateStock(productId, product.getStock().value());

		// 상세 캐시 write-through (재고는 정렬 기준 아님 — ID 리스트 갱신 불필요)
		productCacheManager.refreshProductDetail(productId, () -> productQueryPort.findProductCacheDtoById(productId));
	}


	// 7. 상품 재고 증가 (비관적 쓰기 락 + 상세 캐시 write-through — 보상 트랜잭션)
	@Transactional
	public void increaseStock(Long productId, Long quantity) {

		// 활성 상품 조회 (비관적 쓰기 락 — 동시 재고 변경 경합 방지)
		Product product = productQueryRepository.findActiveByIdForUpdate(productId)
			.orElseThrow(() -> new CoreException(ErrorType.PRODUCT_NOT_FOUND));

		// 재고 증가 (도메인 로직)
		product.increaseStock(quantity);

		// 재고가 증가된 상품 저장
		productCommandRepository.save(product);

		// Read Model 재고 동기화
		readModelRepository.updateStock(productId, product.getStock().value());

		// 상세 캐시 write-through (재고는 정렬 기준 아님 — ID 리스트 갱신 불필요)
		productCacheManager.refreshProductDetail(productId, () -> productQueryPort.findProductCacheDtoById(productId));
	}


	// 8. 상품 좋아요 전체 삭제
	@Transactional
	public void deleteAllProductLikes(Long productId) {
		productLikeCleanupManager.deleteAllByProductId(productId);
	}


	// 9. 장바구니 항목 전체 삭제
	@Transactional
	public void deleteAllCartItems(Long productId) {
		cartItemCleanupManager.deleteAllByProductId(productId);
	}


	// 10. Read Model 동기화 (상품 생성/수정 시 Facade에서 호출)
	@Transactional
	public void syncReadModel(Product product, String brandName) {
		readModelRepository.save(product, brandName);
	}


	// 11. Read Model 브랜드명 일괄 동기화 (브랜드 수정 시 BrandCommandFacade에서 호출)
	@Transactional
	public void syncBrandNameInReadModel(Long brandId, String brandName) {
		readModelRepository.updateBrandName(brandId, brandName);
	}


	// 12. 상품 상세 캐시 write-through (Facade에서 호출 — Read Model projection 기반)
	public void refreshProductDetailCache(Long productId) {
		productCacheManager.refreshProductDetail(productId, () -> productQueryPort.findProductCacheDtoById(productId));
	}


	// 13. 상품 상세 캐시 삭제 (상품 삭제 시 Facade에서 호출)
	public void deleteProductDetailCache(Long productId) {
		productCacheManager.deleteProductDetail(productId);
	}


	// 14. ID 리스트 캐시 write-through — 모든 정렬 (Facade에서 호출)
	public void refreshIdListCacheForAllSorts(Long brandId) {
		for (ProductSortType sort : ProductSortType.values()) {
			refreshIdListCacheForSort(brandId, sort);
		}
	}


	// 15. ID 리스트 캐시 write-through — 특정 정렬 (Facade에서 호출)
	public void refreshIdListCacheForSort(Long brandId, ProductSortType sortType) {
		for (int page = 0; page < MAX_CACHEABLE_PAGE; page++) {
			// brandId 조건 갱신
			refreshSingleIdList(brandId, sortType, page);
			// all 조건 갱신
			refreshSingleIdList(null, sortType, page);
		}
	}


	/**
	 * private method — ID 리스트 캐시 write-through
	 */

	// 단건 ID 리스트 write-through
	private void refreshSingleIdList(Long brandId, ProductSortType sortType, int page) {
		String cacheKey = buildIdListCacheKey(brandId, sortType, page, DEFAULT_PAGE_SIZE);
		ProductSearchCriteria criteria = new ProductSearchCriteria(brandId, sortType);
		PageCriteria pageCriteria = new PageCriteria(page, DEFAULT_PAGE_SIZE);
		productCacheManager.refreshIdList(cacheKey, () -> productQueryPort.searchProductIds(criteria, pageCriteria));
	}

}
