package com.loopers.ordering.order.infrastructure.jpa;


import com.loopers.ordering.order.infrastructure.entity.IdempotencyKeyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;


public interface IdempotencyKeyJpaRepository extends JpaRepository<IdempotencyKeyEntity, Long> {

	/**
	 * 멱등성 키 JPA 리포지토리
	 * 1. 사용자 ID + 요청 ID로 멱등성 키 조회
	 */

	// 1. 사용자 ID + 요청 ID로 멱등성 키 조회
	Optional<IdempotencyKeyEntity> findByUserIdAndRequestId(Long userId, String requestId);

}
