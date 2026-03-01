package com.loopers.engagement.brandlike.infrastructure.repository;

import com.loopers.engagement.brandlike.domain.model.BrandLike;
import com.loopers.engagement.brandlike.domain.repository.BrandLikeCommandRepository;
import com.loopers.engagement.brandlike.infrastructure.entity.BrandLikeEntity;
import com.loopers.engagement.brandlike.infrastructure.jpa.BrandLikeJpaRepository;
import com.loopers.engagement.brandlike.infrastructure.mapper.BrandLikeEntityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;


@Repository
@RequiredArgsConstructor
public class BrandLikeCommandRepositoryImpl implements BrandLikeCommandRepository {

	// jpa
	private final BrandLikeJpaRepository brandLikeJpaRepository;
	// mapper
	private final BrandLikeEntityMapper brandLikeMapper;


	/**
	 * 브랜드 좋아요 명령 리포지토리 구현체
	 * 1. 브랜드 좋아요 저장
	 * 2. 브랜드 좋아요 삭제
	 * 3. 브랜드 ID로 브랜드 좋아요 전체 삭제
	 */

	// 1. 브랜드 좋아요 저장
	@Override
	public BrandLike save(BrandLike brandLike) {

		// 엔티티 변환 및 저장
		BrandLikeEntity entity = brandLikeMapper.toEntity(brandLike);
		BrandLikeEntity savedEntity = brandLikeJpaRepository.save(entity);

		// 도메인 변환 후 반환
		return brandLikeMapper.toDomain(savedEntity);
	}


	// 2. 브랜드 좋아요 삭제
	@Override
	public void delete(BrandLike brandLike) {
		brandLikeJpaRepository.deleteById(brandLike.getId());
	}


	// 3. 브랜드 ID로 브랜드 좋아요 전체 삭제
	@Override
	public void deleteAllByTargetId(Long targetId) {
		brandLikeJpaRepository.deleteAllByTargetTypeAndTargetId("BRAND", targetId);
	}

}
