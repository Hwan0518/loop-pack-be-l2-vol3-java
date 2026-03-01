package com.loopers.catalog.product.application.port.out.client.cart;


public interface CartItemCleanupManager {

	/**
	 * 장바구니 항목 정리 포트
	 * 1. 상품 ID로 장바구니 항목 전체 삭제
	 */

	// 1. 상품 ID로 장바구니 항목 전체 삭제
	void deleteAllByProductId(Long productId);

}
