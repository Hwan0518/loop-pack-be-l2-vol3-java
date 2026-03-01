package com.loopers.catalog.product.infrastructure.repository;


import com.loopers.catalog.product.domain.model.Product;
import com.loopers.catalog.product.domain.repository.ProductQueryRepository;
import com.loopers.catalog.product.infrastructure.jpa.ProductJpaRepository;
import com.loopers.catalog.product.infrastructure.mapper.ProductEntityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;


@Repository
@RequiredArgsConstructor
public class ProductQueryRepositoryImpl implements ProductQueryRepository {

	// jpa
	private final ProductJpaRepository productJpaRepository;
	// mapper
	private final ProductEntityMapper productMapper;


	/**
	 * 상품 조회 리포지토리 구현체
	 * 1. ID로 상품 조회 (미삭제)
	 * 2. 브랜드의 활성 상품 존재 여부 확인
	 * 3. ID로 상품 조회 (삭제 포함)
	 * 4. ID로 상품 조회 (미삭제, 비관적 쓰기 락)
	 * 5. ID 목록으로 활성 상품 일괄 조회
	 */

	// 1. ID로 상품 조회 (미삭제)
	@Override
	public Optional<Product> findActiveById(Long id) {
		return productJpaRepository.findByIdAndDeletedAtIsNull(id)
			.map(productMapper::toDomain);
	}


	// 2. 브랜드의 활성 상품 존재 여부 확인
	@Override
	public boolean existsActiveByBrandId(Long brandId) {
		return productJpaRepository.existsByBrandIdAndDeletedAtIsNull(brandId);
	}


	// 3. ID로 상품 조회 (삭제 포함)
	@Override
	public Optional<Product> findById(Long id) {
		return productJpaRepository.findById(id)
			.map(productMapper::toDomain);
	}


	// 4. ID로 상품 조회 (미삭제, 비관적 쓰기 락)
	@Override
	public Optional<Product> findActiveByIdForUpdate(Long id) {
		return productJpaRepository.findByIdAndDeletedAtIsNullForUpdate(id)
			.map(productMapper::toDomain);
	}


	// 5. ID 목록으로 활성 상품 일괄 조회
	@Override
	public List<Product> findActiveByIds(List<Long> ids) {
		return productJpaRepository.findByIdInAndDeletedAtIsNull(ids).stream()
			.map(productMapper::toDomain)
			.toList();
	}

}
