package com.loopers.catalog.product.infrastructure.repository;


import com.loopers.catalog.product.domain.model.Product;
import com.loopers.catalog.product.domain.repository.ProductCommandRepository;
import com.loopers.catalog.product.infrastructure.entity.ProductEntity;
import com.loopers.catalog.product.infrastructure.jpa.ProductJpaRepository;
import com.loopers.catalog.product.infrastructure.mapper.ProductEntityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;


@Repository
@RequiredArgsConstructor
public class ProductCommandRepositoryImpl implements ProductCommandRepository {

	// jpa
	private final ProductJpaRepository productJpaRepository;
	// mapper
	private final ProductEntityMapper productMapper;


	/**
	 * 상품 명령 리포지토리 구현체
	 * 1. 상품 저장
	 * 2. 상품 삭제 (soft delete)
	 */

	// 1. 상품 저장
	@Override
	public Product save(Product product) {

		// 엔티티로 변환
		ProductEntity entity = productMapper.toEntity(product);

		// 저장
		ProductEntity savedEntity = productJpaRepository.save(entity);

		// 결과 반환
		return productMapper.toDomain(savedEntity);
	}


	// 2. 상품 삭제 (soft delete)
	@Override
	public void delete(Product product) {

		// 엔티티로 변환
		ProductEntity entity = productMapper.toEntity(product);

		// soft delete 처리
		entity.delete();

		// 저장
		productJpaRepository.save(entity);
	}

}
