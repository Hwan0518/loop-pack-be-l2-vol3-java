package com.loopers.ordering.order.infrastructure.mapper;


import com.loopers.ordering.order.domain.model.Order;
import com.loopers.ordering.order.domain.model.OrderItem;
import com.loopers.ordering.order.domain.model.vo.CouponSnapshot;
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

		// 쿠폰 스냅샷 필드 추출 (nullable)
		CouponSnapshot snapshot = order.getCouponSnapshot();

		return OrderEntity.of(
			order.getUserId(),
			order.getRequestId(),
			order.getOriginalTotalPrice(),
			order.getDiscountAmount(),
			order.getTotalPrice(),
			order.getStatus(),
			snapshot != null ? snapshot.issuedCouponId() : null,
			snapshot != null ? snapshot.name() : null,
			snapshot != null ? snapshot.type() : null,
			snapshot != null ? snapshot.value() : null
		);
	}


	// 2. Entity -> Domain 변환
	public Order toDomain(OrderEntity entity, List<OrderItemEntity> itemEntities) {
		List<OrderItem> items = orderItemMapper.toDomains(itemEntities);

		// 쿠폰 스냅샷 복원 (issuedCouponId가 있을 때만)
		CouponSnapshot couponSnapshot = entity.getIssuedCouponId() != null
			? new CouponSnapshot(
				entity.getIssuedCouponId(),
				entity.getCouponSnapshotName(),
				entity.getCouponSnapshotType(),
				entity.getCouponSnapshotValue()
			)
			: null;

		return Order.reconstruct(
			entity.getId(),
			entity.getUserId(),
			entity.getRequestId(),
			entity.getOriginalTotalPrice(),
			entity.getDiscountAmount(),
			entity.getTotalPrice(),
			entity.getStatus(),
			items,
			couponSnapshot,
			entity.getCreatedAt() != null ? entity.getCreatedAt().toLocalDateTime() : null
		);
	}

}
