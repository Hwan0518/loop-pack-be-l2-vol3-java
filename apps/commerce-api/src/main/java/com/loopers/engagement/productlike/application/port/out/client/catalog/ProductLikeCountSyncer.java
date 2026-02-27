package com.loopers.engagement.productlike.application.port.out.client.catalog;


public interface ProductLikeCountSyncer {

	/**
	 * 상품 좋아요 수 동기화 포트
	 * 1. 좋아요 수 증가
	 * 2. 좋아요 수 감소
	 */

	// 1. 좋아요 수 증가
	void increaseLikeCount(Long productId);

	// 2. 좋아요 수 감소
	void decreaseLikeCount(Long productId);

}
