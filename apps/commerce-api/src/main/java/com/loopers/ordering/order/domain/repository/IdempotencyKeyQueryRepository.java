package com.loopers.ordering.order.domain.repository;


import com.loopers.ordering.order.domain.model.IdempotencyKey;

import java.util.Optional;


public interface IdempotencyKeyQueryRepository {

	/**
	 * 멱등성 키 조회 리포지토리
	 * 1. 사용자 ID + 요청 ID로 멱등성 키 조회
	 */

	// 1. 사용자 ID + 요청 ID로 멱등성 키 조회
	Optional<IdempotencyKey> findByUserIdAndRequestId(Long userId, String requestId);

}
