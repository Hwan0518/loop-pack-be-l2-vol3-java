package com.loopers.engagement.productlike.domain.repository;

import com.loopers.engagement.productlike.domain.model.ProductLike;
import com.loopers.engagement.productlike.domain.repository.vo.PageCriteria;
import com.loopers.engagement.productlike.domain.repository.vo.PageResult;

import java.util.Optional;


public interface ProductLikeQueryRepository {

	/**
	 * 상품 좋아요 조회 리포지토리
	 * 1. 사용자 ID와 상품 ID로 좋아요 조회
	 * 2. 사용자 ID와 상품 ID로 좋아요 존재 여부 확인
	 * 3. 사용자 ID로 상품 좋아요 목록 조회 (페이지네이션)
	 */

	// 1. 사용자 ID와 상품 ID로 좋아요 조회
	Optional<ProductLike> findByUserIdAndTargetId(Long userId, Long targetId);

	// 2. 사용자 ID와 상품 ID로 좋아요 존재 여부 확인
	boolean existsByUserIdAndTargetId(Long userId, Long targetId);

	// 3. 사용자 ID로 상품 좋아요 목록 조회 (페이지네이션)
	PageResult<ProductLike> findByUserId(Long userId, PageCriteria pageCriteria);

}
