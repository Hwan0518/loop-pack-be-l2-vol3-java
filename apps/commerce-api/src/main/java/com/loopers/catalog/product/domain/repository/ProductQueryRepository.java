package com.loopers.catalog.product.domain.repository;


import com.loopers.catalog.product.domain.model.Product;

import java.util.Optional;


public interface ProductQueryRepository {

	/**
	 * 상품 조회 리포지토리
	 * 1. ID로 상품 조회 (미삭제)
	 * 2. 브랜드의 활성 상품 존재 여부 확인
	 * 3. ID로 상품 조회 (삭제 포함)
	 */

	// 1. ID로 상품 조회 (미삭제)
	Optional<Product> findActiveById(Long id);

	// 2. 브랜드의 활성 상품 존재 여부 확인
	boolean existsActiveByBrandId(Long brandId);

	// 3. ID로 상품 조회 (삭제 포함)
	Optional<Product> findById(Long id);

}
