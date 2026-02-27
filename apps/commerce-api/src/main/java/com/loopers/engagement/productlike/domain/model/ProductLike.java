package com.loopers.engagement.productlike.domain.model;

import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Objects;


@Getter
public class ProductLike {

	/**
	 * 상품 좋아요
	 * - id: 좋아요 ID
	 * - userId: 사용자 ID
	 * - targetId: 상품 ID
	 * - createdAt: 생성 일시
	 */

	private final Long id;
	private final Long userId;
	private final Long targetId;
	private final LocalDateTime createdAt;


	// 생성자
	private ProductLike(Long id, Long userId, Long targetId, LocalDateTime createdAt) {
		this.id = id;
		this.userId = userId;
		this.targetId = targetId;
		this.createdAt = createdAt;
	}


	/**
	 * 도메인 로직
	 * 1. 상품 좋아요 생성
	 * 2. 상품 좋아요 재생성 (Entity -> Model 매핑용도)
	 */

	// 1. 상품 좋아요 생성
	public static ProductLike create(Long userId, Long targetId) {

		// null 검증
		Objects.requireNonNull(userId, "userId");
		Objects.requireNonNull(targetId, "targetId");

		// targetId 양수 검증
		if (targetId <= 0) {
			throw new CoreException(ErrorType.INVALID_LIKE_TARGET);
		}

		return new ProductLike(null, userId, targetId, null);
	}


	// 2. 상품 좋아요 재생성 (Entity -> Model 매핑용도)
	public static ProductLike reconstruct(Long id, Long userId, Long targetId, LocalDateTime createdAt) {
		return new ProductLike(id, userId, targetId, createdAt);
	}

}
