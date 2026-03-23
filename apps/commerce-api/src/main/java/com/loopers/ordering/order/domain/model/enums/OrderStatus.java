package com.loopers.ordering.order.domain.model.enums;


import java.util.EnumMap;
import java.util.Map;
import java.util.Set;


/**
 * 주문 상태
 * - PENDING_PAYMENT: 결제 대기 중
 * - PAID: 결제 완료 (최종 상태)
 * - PAYMENT_FAILED: 결제 실패
 * - EXPIRED: 주문 만료 (최종 상태)
 *
 * 상태 전이:
 * PENDING_PAYMENT → PAID, PAYMENT_FAILED, EXPIRED
 * PAYMENT_FAILED  → PENDING_PAYMENT, EXPIRED
 * PAID            → (최종 상태)
 * EXPIRED         → (최종 상태)
 */
public enum OrderStatus {

	PENDING_PAYMENT,
	PAID,
	PAYMENT_FAILED,
	EXPIRED;


	// 허용된 상태 전이 맵
	private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS;

	static {
		TRANSITIONS = new EnumMap<>(OrderStatus.class);
		TRANSITIONS.put(PENDING_PAYMENT, Set.of(PAID, PAYMENT_FAILED, EXPIRED));
		TRANSITIONS.put(PAYMENT_FAILED, Set.of(PENDING_PAYMENT, EXPIRED));
		TRANSITIONS.put(PAID, Set.of());
		TRANSITIONS.put(EXPIRED, Set.of());
	}


	/**
	 * 도메인 로직
	 * 1. 상태 전이 가능 여부 확인
	 */

	// 1. 상태 전이 가능 여부 확인
	public boolean canTransitionTo(OrderStatus target) {
		return TRANSITIONS.get(this).contains(target);
	}

}
