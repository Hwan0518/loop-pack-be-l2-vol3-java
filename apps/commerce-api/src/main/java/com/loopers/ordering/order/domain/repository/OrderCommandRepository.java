package com.loopers.ordering.order.domain.repository;


import com.loopers.ordering.order.domain.model.Order;
import com.loopers.ordering.order.domain.model.enums.OrderStatus;


public interface OrderCommandRepository {

	/**
	 * 주문 명령 리포지토리
	 * 1. 주문 저장 (주문 + 주문 항목 함께 저장)
	 * 2. 주문 상태 변경 (status 필드만 업데이트)
	 */

	// 1. 주문 저장 (주문 + 주문 항목 함께 저장)
	Order save(Order order);

	// 2. 주문 상태 변경 (CAS — 현재 status 검증 포함, 변경된 행 수 반환)
	int updateStatus(Long orderId, OrderStatus currentStatus, OrderStatus newStatus);

}
