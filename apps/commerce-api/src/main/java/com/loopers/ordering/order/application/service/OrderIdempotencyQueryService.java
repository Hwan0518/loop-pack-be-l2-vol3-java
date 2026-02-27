package com.loopers.ordering.order.application.service;


import com.loopers.ordering.order.domain.model.IdempotencyKey;
import com.loopers.ordering.order.domain.repository.IdempotencyKeyQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;


@Service
@RequiredArgsConstructor
public class OrderIdempotencyQueryService {

	// repository
	private final IdempotencyKeyQueryRepository idempotencyKeyQueryRepository;


	/**
	 * 주문 멱등성 조회 서비스
	 * 1. 멱등성 키로 기존 주문 ID 조회
	 */

	// 1. 멱등성 키로 기존 주문 ID 조회
	@Transactional(readOnly = true)
	public Optional<Long> findOrderIdByRequestId(Long userId, String requestId) {
		return idempotencyKeyQueryRepository.findByUserIdAndRequestId(userId, requestId)
			.map(IdempotencyKey::getOrderId);
	}

}
