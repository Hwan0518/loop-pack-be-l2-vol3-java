package com.loopers.ordering.order.infrastructure.mapper;


import com.loopers.ordering.order.domain.model.Order;
import com.loopers.ordering.order.domain.model.OrderItem;
import com.loopers.ordering.order.infrastructure.entity.OrderEntity;
import com.loopers.ordering.order.infrastructure.entity.OrderItemEntity;
import org.springframework.stereotype.Component;

import java.util.List;


/**
 * 주문 엔티티 매퍼
 * 1. Domain -> Entity 변환
 * 2. Entity -> Domain 변환
 */
@Component
public class OrderEntityMapper {

	// mapper
	private final OrderItemEntityMapper orderItemMapper;

	public OrderEntityMapper(OrderItemEntityMapper orderItemMapper) {
		this.orderItemMapper = orderItemMapper;
	}


	// 1. Domain -> Entity 변환
	public OrderEntity toEntity(Order order) {
		return OrderEntity.of(
			order.getUserId(),
			order.getTotalPrice()
		);
	}


	// 2. Entity -> Domain 변환
	public Order toDomain(OrderEntity entity, List<OrderItemEntity> itemEntities) {
		List<OrderItem> items = orderItemMapper.toDomains(itemEntities);
		return Order.reconstruct(
			entity.getId(),
			entity.getUserId(),
			entity.getTotalPrice(),
			items,
			entity.getCreatedAt() != null ? entity.getCreatedAt().toLocalDateTime() : null
		);
	}

}
