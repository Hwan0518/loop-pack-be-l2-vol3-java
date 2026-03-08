package com.loopers.catalog.product.application.service;


import com.loopers.catalog.product.application.dto.in.AdminProductCreateInDto;
import com.loopers.catalog.product.application.dto.in.AdminProductUpdateInDto;
import com.loopers.catalog.product.application.port.out.client.cart.CartItemCleanupManager;
import com.loopers.catalog.product.application.port.out.client.engagement.ProductLikeCleanupManager;
import com.loopers.catalog.product.domain.model.Product;
import com.loopers.catalog.product.domain.repository.ProductCommandRepository;
import com.loopers.catalog.product.domain.repository.ProductQueryRepository;
import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
public class ProductCommandService {

	// repository
	private final ProductCommandRepository productCommandRepository;
	private final ProductQueryRepository productQueryRepository;
	// port (@Lazy: Cross-BC 순환 의존 방지 — ProductCommandService ↔ ProductLikeCommandService 간 ACL 경유 순환)
	private final ProductLikeCleanupManager productLikeCleanupManager;
	private final CartItemCleanupManager cartItemCleanupManager;

	public ProductCommandService(
		ProductCommandRepository productCommandRepository,
		ProductQueryRepository productQueryRepository,
		@Lazy ProductLikeCleanupManager productLikeCleanupManager,
		@Lazy CartItemCleanupManager cartItemCleanupManager
	) {
		this.productCommandRepository = productCommandRepository;
		this.productQueryRepository = productQueryRepository;
		this.productLikeCleanupManager = productLikeCleanupManager;
		this.cartItemCleanupManager = cartItemCleanupManager;
	}


	/**
	 * 상품 명령 서비스
	 * 1. 상품 생성
	 * 2. 상품 수정
	 * 3. 상품 삭제
	 * 4. 좋아요 수 증가 (원자적 카운터)
	 * 5. 좋아요 수 감소 (원자적 카운터)
	 * 6. 상품 재고 차감 (비관적 쓰기 락)
	 * 7. 상품 좋아요 전체 삭제
	 * 8. 장바구니 항목 전체 삭제
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
	}


	// 4. 좋아요 수 증가 (원자적 카운터 — 단일 UPDATE SQL로 동시성 안전)
	@Transactional
	public void increaseLikeCount(Long productId) {
		productCommandRepository.increaseLikeCount(productId);
	}


	// 5. 좋아요 수 감소 (원자적 카운터 — 단일 UPDATE SQL로 동시성 안전)
	@Transactional
	public void decreaseLikeCount(Long productId) {
		productCommandRepository.decreaseLikeCount(productId);
	}


	// 6. 상품 재고 차감 (비관적 쓰기 락)
	@Transactional
	public void decreaseStock(Long productId, Long quantity) {

		// 활성 상품 조회 (비관적 쓰기 락 — 동시 재고 차감 경합 방지)
		Product product = productQueryRepository.findActiveByIdForUpdate(productId)
			.orElseThrow(() -> new CoreException(ErrorType.PRODUCT_NOT_FOUND));

		// 재고 차감 (도메인 로직 — 재고 부족 시 PRODUCT_OUT_OF_STOCK 예외)
		product.decreaseStock(quantity);

		// 재고가 차감된 상품 저장
		productCommandRepository.save(product);
	}


	// 7. 상품 좋아요 전체 삭제
	@Transactional
	public void deleteAllProductLikes(Long productId) {
		productLikeCleanupManager.deleteAllByProductId(productId);
	}


	// 8. 장바구니 항목 전체 삭제
	@Transactional
	public void deleteAllCartItems(Long productId) {
		cartItemCleanupManager.deleteAllByProductId(productId);
	}

}
