package com.loopers.catalog.product.infrastructure.repository;


import com.loopers.catalog.product.domain.model.Product;
import com.loopers.catalog.product.domain.repository.ProductCommandRepository;
import com.loopers.catalog.product.infrastructure.entity.ProductEntity;
import com.loopers.catalog.product.infrastructure.jpa.ProductJpaRepository;
import com.loopers.catalog.product.infrastructure.mapper.ProductEntityMapper;
import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
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
	 * 3. 좋아요 수 원자적 증가
	 * 4. 좋아요 수 원자적 감소
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


	// 3. 좋아요 수 원자적 증가
	@Override
	public void increaseLikeCount(Long productId) {

		// 원자적 증가 (단일 SQL UPDATE)
		int updatedRows = productJpaRepository.increaseLikeCount(productId);

		// 대상 상품 미존재 시 예외
		if (updatedRows == 0) {
			throw new CoreException(ErrorType.PRODUCT_NOT_FOUND);
		}
	}


	// 4. 좋아요 수 원자적 감소
	@Override
	public void decreaseLikeCount(Long productId) {

		// 원자적 감소 (단일 SQL UPDATE — 0 이하로 내려가지 않음)
		int updatedRows = productJpaRepository.decreaseLikeCount(productId);

		// 대상 상품 미존재 시 예외
		if (updatedRows == 0) {
			throw new CoreException(ErrorType.PRODUCT_NOT_FOUND);
		}
	}

}
