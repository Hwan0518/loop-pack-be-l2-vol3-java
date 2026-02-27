package com.loopers.engagement.brandlike.domain.repository;

import com.loopers.engagement.brandlike.domain.model.BrandLike;
import com.loopers.engagement.brandlike.domain.repository.vo.PageCriteria;
import com.loopers.engagement.brandlike.domain.repository.vo.PageResult;

import java.util.Optional;


public interface BrandLikeQueryRepository {

	/**
	 * 브랜드 좋아요 조회 리포지토리
	 * 1. 사용자 ID와 브랜드 ID로 좋아요 조회
	 * 2. 사용자 ID와 브랜드 ID로 좋아요 존재 여부 확인
	 * 3. 사용자 ID로 브랜드 좋아요 목록 조회 (페이지네이션)
	 */

	// 1. 사용자 ID와 브랜드 ID로 좋아요 조회
	Optional<BrandLike> findByUserIdAndTargetId(Long userId, Long targetId);

	// 2. 사용자 ID와 브랜드 ID로 좋아요 존재 여부 확인
	boolean existsByUserIdAndTargetId(Long userId, Long targetId);

	// 3. 사용자 ID로 브랜드 좋아요 목록 조회 (페이지네이션)
	PageResult<BrandLike> findByUserId(Long userId, PageCriteria pageCriteria);

}
