package com.loopers.ordering.order.infrastructure.repository;


import com.loopers.ordering.order.domain.model.IdempotencyKey;
import com.loopers.ordering.order.domain.repository.IdempotencyKeyQueryRepository;
import com.loopers.ordering.order.infrastructure.jpa.IdempotencyKeyJpaRepository;
import com.loopers.ordering.order.infrastructure.mapper.IdempotencyKeyEntityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;


@Repository
@RequiredArgsConstructor
public class IdempotencyKeyQueryRepositoryImpl implements IdempotencyKeyQueryRepository {

	// jpa
	private final IdempotencyKeyJpaRepository idempotencyKeyJpaRepository;
	// mapper
	private final IdempotencyKeyEntityMapper idempotencyKeyMapper;


	/**
	 * 멱등성 키 조회 리포지토리 구현체
	 * 1. 사용자 ID + 요청 ID로 멱등성 키 조회
	 */

	// 1. 사용자 ID + 요청 ID로 멱등성 키 조회
	@Override
	public Optional<IdempotencyKey> findByUserIdAndRequestId(Long userId, String requestId) {
		return idempotencyKeyJpaRepository.findByUserIdAndRequestId(userId, requestId)
			.map(idempotencyKeyMapper::toDomain);
	}

}
