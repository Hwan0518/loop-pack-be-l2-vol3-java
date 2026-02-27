package com.loopers.engagement.productlike.infrastructure.mapper;

import com.loopers.engagement.productlike.domain.model.ProductLike;
import com.loopers.engagement.productlike.infrastructure.entity.ProductLikeEntity;
import org.springframework.stereotype.Component;


@Component
public class ProductLikeEntityMapper {

	/**
	 * 상품 좋아요 엔티티 매퍼
	 * 1. 도메인 -> 엔티티 변환
	 * 2. 엔티티 -> 도메인 변환
	 */

	// 1. 도메인 -> 엔티티 변환
	public ProductLikeEntity toEntity(ProductLike productLike) {
		return ProductLikeEntity.of(productLike.getUserId(), productLike.getTargetId());
	}


	// 2. 엔티티 -> 도메인 변환
	public ProductLike toDomain(ProductLikeEntity entity) {
		return ProductLike.reconstruct(
			entity.getId(),
			entity.getUserId(),
			entity.getTargetId(),
			entity.getCreatedAt() != null ? entity.getCreatedAt().toLocalDateTime() : null
		);
	}

}
