package com.loopers.catalog.product.application.port.out.client.engagement;


public interface ProductLikeCleanupManager {

	/**
	 * 상품 좋아요 정리 포트
	 * 1. 상품 ID로 좋아요 전체 삭제
	 */

	// 1. 상품 ID로 좋아요 전체 삭제
	void deleteAllByProductId(Long productId);

}
