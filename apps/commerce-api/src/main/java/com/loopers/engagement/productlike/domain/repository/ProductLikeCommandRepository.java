package com.loopers.engagement.productlike.domain.repository;

import com.loopers.engagement.productlike.domain.model.ProductLike;


public interface ProductLikeCommandRepository {

	/**
	 * 상품 좋아요 명령 리포지토리
	 * 1. 상품 좋아요 저장
	 * 2. 상품 좋아요 삭제
	 * 3. 상품 ID로 상품 좋아요 전체 삭제
	 */

	// 1. 상품 좋아요 저장
	ProductLike save(ProductLike productLike);

	// 2. 상품 좋아요 삭제
	void delete(ProductLike productLike);

	// 3. 상품 ID로 상품 좋아요 전체 삭제
	void deleteAllByTargetId(Long targetId);

}
