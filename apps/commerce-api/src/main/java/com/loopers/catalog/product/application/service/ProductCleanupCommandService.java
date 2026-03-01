package com.loopers.catalog.product.application.service;


import com.loopers.catalog.product.application.port.out.client.cart.CartItemCleanupManager;
import com.loopers.catalog.product.application.port.out.client.engagement.ProductLikeCleanupManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


/**
 * 상품 부수효과 정리 서비스
 * 상품 삭제 시 관련 Cross-BC 데이터 정리를 담당한다.
 */
@Service
@RequiredArgsConstructor
public class ProductCleanupCommandService {

	// port
	private final ProductLikeCleanupManager productLikeCleanupManager;
	private final CartItemCleanupManager cartItemCleanupManager;


	/**
	 * 상품 부수효과 정리
	 * 1. 상품 좋아요 전체 삭제
	 * 2. 장바구니 항목 전체 삭제
	 */

	// 1. 상품 좋아요 전체 삭제
	@Transactional
	public void deleteAllProductLikes(Long productId) {
		productLikeCleanupManager.deleteAllByProductId(productId);
	}


	// 2. 장바구니 항목 전체 삭제
	@Transactional
	public void deleteAllCartItems(Long productId) {
		cartItemCleanupManager.deleteAllByProductId(productId);
	}

}
