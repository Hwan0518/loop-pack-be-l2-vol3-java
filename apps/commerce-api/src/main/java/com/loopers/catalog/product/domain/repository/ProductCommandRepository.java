package com.loopers.catalog.product.domain.repository;


import com.loopers.catalog.product.domain.model.Product;


public interface ProductCommandRepository {

	/**
	 * 상품 명령 리포지토리
	 * 1. 상품 저장
	 * 2. 상품 삭제 (soft delete)
	 * 3. 좋아요 수 원자적 증가
	 * 4. 좋아요 수 원자적 감소
	 */

	// 1. 상품 저장
	Product save(Product product);

	// 2. 상품 삭제 (soft delete)
	void delete(Product product);

	// 3. 좋아요 수 원자적 증가
	void increaseLikeCount(Long productId);

	// 4. 좋아요 수 원자적 감소
	void decreaseLikeCount(Long productId);

}
