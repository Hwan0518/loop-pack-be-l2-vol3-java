package com.loopers.catalog.product.infrastructure.jpa;


import com.loopers.catalog.product.infrastructure.entity.ProductEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;


public interface ProductJpaRepository extends JpaRepository<ProductEntity, Long> {

	/**
	 * 상품 JPA 리포지토리
	 * 1. ID로 활성 상품 엔티티 조회
	 * 2. 브랜드의 활성 상품 존재 여부 확인
	 */

	// 1. ID로 활성 상품 엔티티 조회
	Optional<ProductEntity> findByIdAndDeletedAtIsNull(Long id);

	// 2. 브랜드의 활성 상품 존재 여부 확인
	boolean existsByBrandIdAndDeletedAtIsNull(Long brandId);

}
