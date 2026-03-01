package com.loopers.engagement.brandlike.domain.repository;

import com.loopers.engagement.brandlike.domain.model.BrandLike;


public interface BrandLikeCommandRepository {

	/**
	 * 브랜드 좋아요 명령 리포지토리
	 * 1. 브랜드 좋아요 저장
	 * 2. 브랜드 좋아요 삭제
	 * 3. 브랜드 ID로 브랜드 좋아요 전체 삭제
	 */

	// 1. 브랜드 좋아요 저장
	BrandLike save(BrandLike brandLike);

	// 2. 브랜드 좋아요 삭제
	void delete(BrandLike brandLike);

	// 3. 브랜드 ID로 브랜드 좋아요 전체 삭제
	void deleteAllByTargetId(Long targetId);

}
