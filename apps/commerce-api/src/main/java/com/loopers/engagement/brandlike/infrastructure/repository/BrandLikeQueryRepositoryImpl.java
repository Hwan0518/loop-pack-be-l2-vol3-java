package com.loopers.engagement.brandlike.infrastructure.repository;

import com.loopers.engagement.brandlike.domain.model.BrandLike;
import com.loopers.engagement.brandlike.domain.repository.BrandLikeQueryRepository;
import com.loopers.engagement.brandlike.domain.repository.vo.PageCriteria;
import com.loopers.engagement.brandlike.domain.repository.vo.PageResult;
import com.loopers.engagement.brandlike.infrastructure.entity.BrandLikeEntity;
import com.loopers.engagement.brandlike.infrastructure.jpa.BrandLikeJpaRepository;
import com.loopers.engagement.brandlike.infrastructure.mapper.BrandLikeEntityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.Optional;


@Repository
@RequiredArgsConstructor
public class BrandLikeQueryRepositoryImpl implements BrandLikeQueryRepository {

	// jpa
	private final BrandLikeJpaRepository brandLikeJpaRepository;
	// mapper
	private final BrandLikeEntityMapper brandLikeMapper;

	private static final String TARGET_TYPE = "BRAND";


	/**
	 * 브랜드 좋아요 조회 리포지토리 구현체
	 * 1. 사용자 ID와 브랜드 ID로 좋아요 조회
	 * 2. 사용자 ID와 브랜드 ID로 좋아요 존재 여부 확인
	 * 3. 사용자 ID로 브랜드 좋아요 목록 조회 (페이지네이션)
	 */

	// 1. 사용자 ID와 브랜드 ID로 좋아요 조회
	@Override
	public Optional<BrandLike> findByUserIdAndTargetId(Long userId, Long targetId) {
		return brandLikeJpaRepository
			.findByUserIdAndTargetTypeAndTargetId(userId, TARGET_TYPE, targetId)
			.map(brandLikeMapper::toDomain);
	}


	// 2. 사용자 ID와 브랜드 ID로 좋아요 존재 여부 확인
	@Override
	public boolean existsByUserIdAndTargetId(Long userId, Long targetId) {
		return brandLikeJpaRepository
			.existsByUserIdAndTargetTypeAndTargetId(userId, TARGET_TYPE, targetId);
	}


	// 3. 사용자 ID로 브랜드 좋아요 목록 조회 (페이지네이션)
	@Override
	public PageResult<BrandLike> findByUserId(Long userId, PageCriteria pageCriteria) {

		// Spring Page 조회 (최신순 정렬)
		PageRequest pageRequest = PageRequest.of(
			pageCriteria.page(), pageCriteria.size(), Sort.by(Sort.Direction.DESC, "id"));
		Page<BrandLikeEntity> page = brandLikeJpaRepository
			.findByUserIdAndTargetType(userId, TARGET_TYPE, pageRequest);

		// 도메인 PageResult 변환
		return new PageResult<>(
			page.getContent().stream().map(brandLikeMapper::toDomain).toList(),
			pageCriteria.page(),
			pageCriteria.size(),
			page.getTotalElements()
		);
	}

}
