package com.loopers.ordering.order.domain.repository;


import com.loopers.ordering.order.domain.model.Order;


public interface OrderCommandRepository {

	/**
	 * 주문 명령 리포지토리
	 * 1. 주문 저장 (주문 + 주문 항목 함께 저장)
	 */

	// 1. 주문 저장 (주문 + 주문 항목 함께 저장)
	Order save(Order order);

}
