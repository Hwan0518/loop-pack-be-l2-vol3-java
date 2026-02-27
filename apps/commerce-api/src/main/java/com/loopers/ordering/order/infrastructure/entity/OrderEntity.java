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
 * 주문 엔티티
 * - userId: 주문자 ID
 * - totalPrice: 주문 총액
 */
@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderEntity extends BaseEntity {

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "total_price", nullable = false, precision = 15, scale = 2)
	private BigDecimal totalPrice;


	private OrderEntity(Long userId, BigDecimal totalPrice) {
		this.userId = userId;
		this.totalPrice = totalPrice;
	}


	public static OrderEntity of(Long userId, BigDecimal totalPrice) {
		return new OrderEntity(userId, totalPrice);
	}

}
