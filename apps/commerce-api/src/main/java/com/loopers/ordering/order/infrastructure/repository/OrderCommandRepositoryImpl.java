package com.loopers.ordering.order.infrastructure.repository;


import com.loopers.ordering.order.domain.model.Order;
import com.loopers.ordering.order.domain.model.enums.OrderStatus;
import com.loopers.ordering.order.domain.repository.OrderCommandRepository;
import com.loopers.ordering.order.infrastructure.entity.OrderEntity;
import com.loopers.ordering.order.infrastructure.entity.OrderItemEntity;
import com.loopers.ordering.order.infrastructure.jpa.OrderItemJpaRepository;
import com.loopers.ordering.order.infrastructure.jpa.OrderJpaRepository;
import com.loopers.ordering.order.infrastructure.mapper.OrderEntityMapper;
import com.loopers.ordering.order.infrastructure.mapper.OrderItemEntityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;


@Repository
@RequiredArgsConstructor
public class OrderCommandRepositoryImpl implements OrderCommandRepository {

	// jpa
	private final OrderJpaRepository orderJpaRepository;
	private final OrderItemJpaRepository orderItemJpaRepository;
	// mapper
	private final OrderEntityMapper orderMapper;
	private final OrderItemEntityMapper orderItemMapper;


	/**
	 * 주문 명령 리포지토리 구현체
	 * 1. 주문 저장 (주문 + 주문 항목 함께 저장)
	 * 2. 주문 상태 변경 (JPQL UPDATE)
	 */

	// 1. 주문 저장 (주문 + 주문 항목 함께 저장)
	@Override
	public Order save(Order order) {

		// 주문 엔티티 변환 및 저장
		OrderEntity orderEntity = orderMapper.toEntity(order);
		OrderEntity savedOrderEntity = orderJpaRepository.save(orderEntity);

		// 주문 항목 엔티티 변환 및 저장
		List<OrderItemEntity> itemEntities = orderItemMapper.toEntities(order.getItems(), savedOrderEntity.getId());
		List<OrderItemEntity> savedItemEntities = orderItemJpaRepository.saveAll(itemEntities);

		// 도메인 변환 후 반환
		return orderMapper.toDomain(savedOrderEntity, savedItemEntities);
	}


	// 2. 주문 상태 변경 (CAS — 현재 status 검증 포함)
	@Override
	public int updateStatus(Long orderId, OrderStatus currentStatus, OrderStatus newStatus) {
		return orderJpaRepository.updateStatus(orderId, currentStatus, newStatus);
	}

}
