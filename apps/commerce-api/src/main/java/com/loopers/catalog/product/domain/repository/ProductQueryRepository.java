package com.loopers.catalog.product.domain.repository;


import com.loopers.catalog.product.domain.model.Product;

import java.util.List;
import java.util.Optional;


public interface ProductQueryRepository {

	/**
	 * 상품 조회 리포지토리
	 * 1. ID로 상품 조회 (미삭제)
	 * 2. 브랜드의 활성 상품 존재 여부 확인
	 * 3. ID로 상품 조회 (삭제 포함)
	 * 4. ID로 상품 조회 (미삭제, 비관적 쓰기 락)
	 * 5. ID 목록으로 활성 상품 일괄 조회
	 */

	// 1. ID로 상품 조회 (미삭제)
	Optional<Product> findActiveById(Long id);

	// 2. 브랜드의 활성 상품 존재 여부 확인
	boolean existsActiveByBrandId(Long brandId);

	// 3. ID로 상품 조회 (삭제 포함)
	Optional<Product> findById(Long id);

	// 4. ID로 상품 조회 (미삭제, 비관적 쓰기 락)
	Optional<Product> findActiveByIdForUpdate(Long id);

	// 5. ID 목록으로 활성 상품 일괄 조회
	List<Product> findActiveByIds(List<Long> ids);

}
