package com.loopers.ordering.order.infrastructure.entity;


import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;


/**
 * 주문 항목 엔티티
 * - orderId: 주문 ID
 * - productId: 상품 ID
 * - snapshotName: 주문 시점 상품명 스냅샷
 * - snapshotPrice: 주문 시점 상품 가격 스냅샷
 * - quantity: 주문 수량
 */
@Entity
@Table(name = "order_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItemEntity extends BaseEntity {

	@Column(name = "order_id", nullable = false)
	private Long orderId;

	@Column(name = "product_id", nullable = false)
	private Long productId;

	@Column(name = "snapshot_name", nullable = false, length = 200)
	private String snapshotName;

	@Column(name = "snapshot_price", nullable = false, precision = 15, scale = 2)
	private BigDecimal snapshotPrice;

	@Column(name = "quantity", nullable = false)
	private Long quantity;


	private OrderItemEntity(Long orderId, Long productId, String snapshotName, BigDecimal snapshotPrice, Long quantity) {
		this.orderId = orderId;
		this.productId = productId;
		this.snapshotName = snapshotName;
		this.snapshotPrice = snapshotPrice;
		this.quantity = quantity;
	}


	public static OrderItemEntity of(Long orderId, Long productId, String snapshotName,
		BigDecimal snapshotPrice, Long quantity) {
		return new OrderItemEntity(orderId, productId, snapshotName, snapshotPrice, quantity);
	}

}
