package com.loopers.ordering.order.infrastructure.jpa;


import com.loopers.ordering.order.infrastructure.entity.OrderItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;


public interface OrderItemJpaRepository extends JpaRepository<OrderItemEntity, Long> {

	/**
	 * 주문 항목 JPA 리포지토리
	 * 1. 주문 ID로 주문 항목 목록 조회
	 * 2. 주문 ID 목록으로 주문 항목 목록 조회
	 */

	// 1. 주문 ID로 주문 항목 목록 조회
	List<OrderItemEntity> findByOrderId(Long orderId);

	// 2. 주문 ID 목록으로 주문 항목 목록 조회
	List<OrderItemEntity> findByOrderIdIn(List<Long> orderIds);

}
