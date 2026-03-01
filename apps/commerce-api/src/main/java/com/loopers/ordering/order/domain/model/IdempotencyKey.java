package com.loopers.ordering.order.domain.model;


import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Objects;


@Getter
public class IdempotencyKey {

	/**
	 * 멱등성 키
	 * - id: 멱등성 키 ID
	 * - userId: 사용자 ID
	 * - requestId: 요청 ID (클라이언트 제공)
	 * - orderId: 연결된 주문 ID
	 * - createdAt: 생성 일시
	 */

	private final Long id;
	private final Long userId;
	private final String requestId;
	private final Long orderId;
	private final LocalDateTime createdAt;


	// 생성자
	private IdempotencyKey(Long id, Long userId, String requestId, Long orderId, LocalDateTime createdAt) {
		this.id = id;
		this.userId = userId;
		this.requestId = requestId;
		this.orderId = orderId;
		this.createdAt = createdAt;
	}


	/**
	 * 도메인 로직
	 * 1. 멱등성 키 생성
	 * 2. 멱등성 키 재생성 (Entity -> Model 매핑용도)
	 */

	// 1. 멱등성 키 생성
	public static IdempotencyKey create(Long userId, String requestId, Long orderId) {

		// userId null 검증
		Objects.requireNonNull(userId, "userId");

		// requestId 검증
		validateRequestId(requestId);

		// orderId null 검증
		Objects.requireNonNull(orderId, "orderId");

		return new IdempotencyKey(null, userId, requestId, orderId, null);
	}


	// 2. 멱등성 키 재생성 (Entity -> Model 매핑용도)
	public static IdempotencyKey reconstruct(Long id, Long userId, String requestId, Long orderId,
		LocalDateTime createdAt) {
		return new IdempotencyKey(id, userId, requestId, orderId, createdAt);
	}


	/**
	 * private 메서드
	 * 1. 요청 ID 검증
	 */

	// 1. 요청 ID 검증
	private static void validateRequestId(String requestId) {
		if (requestId == null || requestId.isBlank()) {
			throw new CoreException(ErrorType.INVALID_REQUEST_ID);
		}
	}

}
