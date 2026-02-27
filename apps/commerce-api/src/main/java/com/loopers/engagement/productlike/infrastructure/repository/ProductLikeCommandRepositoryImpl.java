package com.loopers.engagement.productlike.infrastructure.repository;

import com.loopers.engagement.productlike.domain.model.ProductLike;
import com.loopers.engagement.productlike.domain.repository.ProductLikeCommandRepository;
import com.loopers.engagement.productlike.infrastructure.entity.ProductLikeEntity;
import com.loopers.engagement.productlike.infrastructure.jpa.ProductLikeJpaRepository;
import com.loopers.engagement.productlike.infrastructure.mapper.ProductLikeEntityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;


@Repository
@RequiredArgsConstructor
public class ProductLikeCommandRepositoryImpl implements ProductLikeCommandRepository {

	// jpa
	private final ProductLikeJpaRepository productLikeJpaRepository;
	// mapper
	private final ProductLikeEntityMapper productLikeMapper;


	/**
	 * 상품 좋아요 명령 리포지토리 구현체
	 * 1. 상품 좋아요 저장
	 * 2. 상품 좋아요 삭제
	 * 3. 상품 ID로 상품 좋아요 전체 삭제
	 */

	// 1. 상품 좋아요 저장
	@Override
	public ProductLike save(ProductLike productLike) {

		// 엔티티 변환 및 저장
		ProductLikeEntity entity = productLikeMapper.toEntity(productLike);
		ProductLikeEntity savedEntity = productLikeJpaRepository.save(entity);

		// 도메인 변환 후 반환
		return productLikeMapper.toDomain(savedEntity);
	}


	// 2. 상품 좋아요 삭제
	@Override
	public void delete(ProductLike productLike) {
		productLikeJpaRepository.deleteById(productLike.getId());
	}


	// 3. 상품 ID로 상품 좋아요 전체 삭제
	@Override
	public void deleteAllByTargetId(Long targetId) {
		productLikeJpaRepository.deleteAllByTargetTypeAndTargetId("PRODUCT", targetId);
	}

}
