package com.loopers.ordering.order.domain.model;


import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;


@Getter
public class Order {

	/**
	 * 주문
	 * - id: 주문 ID
	 * - userId: 주문자 ID
	 * - totalPrice: 주문 총액
	 * - items: 주문 항목 목록
	 * - createdAt: 주문 생성 일시
	 */

	private final Long id;
	private final Long userId;
	private final BigDecimal totalPrice;
	private final List<OrderItem> items;
	private final LocalDateTime createdAt;


	// 생성자
	private Order(Long id, Long userId, BigDecimal totalPrice, List<OrderItem> items, LocalDateTime createdAt) {
		this.id = id;
		this.userId = userId;
		this.totalPrice = totalPrice;
		this.items = items;
		this.createdAt = createdAt;
	}


	/**
	 * 도메인 로직
	 * 1. 주문 생성
	 * 2. 주문 재생성 (Entity -> Model 매핑용도)
	 */

	// 1. 주문 생성
	public static Order create(Long userId, BigDecimal totalPrice, List<OrderItem> items) {

		// userId null 검증
		Objects.requireNonNull(userId, "userId");

		// totalPrice 검증
		validateTotalPrice(totalPrice);

		// items 검증
		validateItems(items);

		return new Order(null, userId, totalPrice, items, null);
	}


	// 2. 주문 재생성 (Entity -> Model 매핑용도)
	public static Order reconstruct(Long id, Long userId, BigDecimal totalPrice,
		List<OrderItem> items, LocalDateTime createdAt) {
		return new Order(id, userId, totalPrice, items, createdAt);
	}


	/**
	 * private 메서드
	 * 1. 총액 검증
	 * 2. 주문 항목 검증
	 */

	// 1. 총액 검증
	private static void validateTotalPrice(BigDecimal totalPrice) {
		if (totalPrice == null || totalPrice.compareTo(BigDecimal.ZERO) < 0) {
			throw new CoreException(ErrorType.INVALID_ORDER_TOTAL_PRICE);
		}
	}


	// 2. 주문 항목 검증
	private static void validateItems(List<OrderItem> items) {
		if (items == null || items.isEmpty()) {
			throw new CoreException(ErrorType.ORDER_EMPTY_ITEMS);
		}
	}

}
