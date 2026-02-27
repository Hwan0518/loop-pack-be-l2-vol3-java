package com.loopers.ordering.order.infrastructure.mapper;


import com.loopers.ordering.order.domain.model.OrderItem;
import com.loopers.ordering.order.domain.model.vo.SnapshotName;
import com.loopers.ordering.order.domain.model.vo.SnapshotPrice;
import com.loopers.ordering.order.infrastructure.entity.OrderItemEntity;
import org.springframework.stereotype.Component;

import java.util.List;


/**
 * 주문 항목 엔티티 매퍼
 * 1. Domain -> Entity 변환
 * 2. Domain 목록 -> Entity 목록 변환
 * 3. Entity -> Domain 변환
 * 4. Entity 목록 -> Domain 목록 변환
 */
@Component
public class OrderItemEntityMapper {

	// 1. Domain -> Entity 변환
	public OrderItemEntity toEntity(OrderItem orderItem, Long orderId) {
		return OrderItemEntity.of(
			orderId,
			orderItem.getProductId(),
			orderItem.getSnapshotName().value(),
			orderItem.getSnapshotPrice().value(),
			orderItem.getQuantity()
		);
	}


	// 2. Domain 목록 -> Entity 목록 변환
	public List<OrderItemEntity> toEntities(List<OrderItem> orderItems, Long orderId) {
		return orderItems.stream()
			.map(item -> toEntity(item, orderId))
			.toList();
	}


	// 3. Entity -> Domain 변환
	public OrderItem toDomain(OrderItemEntity entity) {
		return OrderItem.reconstruct(
			entity.getId(),
			entity.getProductId(),
			SnapshotName.from(entity.getSnapshotName()),
			SnapshotPrice.from(entity.getSnapshotPrice()),
			entity.getQuantity()
		);
	}


	// 4. Entity 목록 -> Domain 목록 변환
	public List<OrderItem> toDomains(List<OrderItemEntity> entities) {
		return entities.stream()
			.map(this::toDomain)
			.toList();
	}

}
