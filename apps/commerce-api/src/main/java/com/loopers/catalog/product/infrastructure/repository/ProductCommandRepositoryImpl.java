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

		// managed entity 조회 (detached entity merge 시 stale state 충돌 방지)
		ProductEntity entity = productJpaRepository.findById(product.getId())
			.orElseThrow();

		// soft delete 처리
		entity.delete();

		// saveAndFlush로 즉시 DB 반영 (같은 TX 내 후속 조회에서 정합성 보장)
		productJpaRepository.saveAndFlush(entity);
	}

}
