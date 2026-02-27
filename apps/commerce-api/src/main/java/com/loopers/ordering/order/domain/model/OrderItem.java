package com.loopers.ordering.order.domain.model;


import com.loopers.ordering.order.domain.model.vo.SnapshotName;
import com.loopers.ordering.order.domain.model.vo.SnapshotPrice;
import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.Objects;


@Getter
public class OrderItem {

	/**
	 * 주문 항목
	 * - id: 주문 항목 ID
	 * - productId: 상품 ID
	 * - snapshotName: 주문 시점 상품명 스냅샷
	 * - snapshotPrice: 주문 시점 상품 가격 스냅샷
	 * - quantity: 주문 수량
	 */

	private final Long id;
	private final Long productId;
	private final SnapshotName snapshotName;
	private final SnapshotPrice snapshotPrice;
	private final Long quantity;


	// 생성자
	private OrderItem(Long id, Long productId, SnapshotName snapshotName, SnapshotPrice snapshotPrice, Long quantity) {
		this.id = id;
		this.productId = productId;
		this.snapshotName = snapshotName;
		this.snapshotPrice = snapshotPrice;
		this.quantity = quantity;
	}


	/**
	 * 도메인 로직
	 * 1. 주문 항목 생성
	 * 2. 주문 항목 재생성 (Entity -> Model 매핑용도)
	 * 3. 소계 금액 계산
	 */

	// 1. 주문 항목 생성
	public static OrderItem create(Long productId, String snapshotName, BigDecimal snapshotPrice, Long quantity) {

		// productId null 검증
		Objects.requireNonNull(productId, "productId");

		// 수량 검증
		validateQuantity(quantity);

		// VO 생성 (내부에서 유효성 검증)
		SnapshotName name = SnapshotName.create(snapshotName);
		SnapshotPrice price = SnapshotPrice.create(snapshotPrice);

		return new OrderItem(null, productId, name, price, quantity);
	}


	// 2. 주문 항목 재생성 (Entity -> Model 매핑용도)
	public static OrderItem reconstruct(Long id, Long productId, SnapshotName snapshotName,
		SnapshotPrice snapshotPrice, Long quantity) {
		return new OrderItem(id, productId, snapshotName, snapshotPrice, quantity);
	}


	// 3. 소계 금액 계산
	public BigDecimal getSubtotal() {
		return snapshotPrice.value().multiply(BigDecimal.valueOf(quantity));
	}


	/**
	 * private 메서드
	 * 1. 수량 검증
	 */

	// 1. 수량 검증
	private static void validateQuantity(Long quantity) {
		if (quantity == null || quantity <= 0) {
			throw new CoreException(ErrorType.INVALID_ORDER_QUANTITY);
		}
	}

}
