package com.loopers.engagement.brandlike.infrastructure.mapper;

import com.loopers.engagement.brandlike.domain.model.BrandLike;
import com.loopers.engagement.brandlike.infrastructure.entity.BrandLikeEntity;
import org.springframework.stereotype.Component;


@Component
public class BrandLikeEntityMapper {

	/**
	 * 브랜드 좋아요 엔티티 매퍼
	 * 1. 도메인 -> 엔티티 변환
	 * 2. 엔티티 -> 도메인 변환
	 */

	// 1. 도메인 -> 엔티티 변환
	public BrandLikeEntity toEntity(BrandLike brandLike) {
		return BrandLikeEntity.of(brandLike.getUserId(), brandLike.getTargetId());
	}


	// 2. 엔티티 -> 도메인 변환
	public BrandLike toDomain(BrandLikeEntity entity) {
		return BrandLike.reconstruct(
			entity.getId(),
			entity.getUserId(),
			entity.getTargetId(),
			entity.getCreatedAt() != null ? entity.getCreatedAt().toLocalDateTime() : null
		);
	}

}
