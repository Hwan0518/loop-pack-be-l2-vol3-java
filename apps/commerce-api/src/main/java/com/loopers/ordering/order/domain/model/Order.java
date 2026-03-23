package com.loopers.ordering.order.domain.model;


import com.loopers.ordering.order.domain.model.enums.OrderStatus;
import com.loopers.ordering.order.domain.model.vo.CouponSnapshot;
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
	 * - requestId: 멱등성 보장용 요청 ID
	 * - originalTotalPrice: 쿠폰 적용 전 총액
	 * - discountAmount: 할인 금액 (0 = 쿠폰 미사용)
	 * - totalPrice: 최종 결제 금액 (= originalTotalPrice - discountAmount)
	 * - status: 주문 상태 (PENDING_PAYMENT → PAID / PAYMENT_FAILED / EXPIRED)
	 * - items: 주문 항목 목록
	 * - couponSnapshot: 적용된 쿠폰 스냅샷 (nullable)
	 * - createdAt: 주문 생성 일시
	 */

	private final Long id;
	private final Long userId;
	private final String requestId;
	private final BigDecimal originalTotalPrice;
	private final BigDecimal discountAmount;
	private final BigDecimal totalPrice;
	private OrderStatus status;
	private final List<OrderItem> items;
	private final CouponSnapshot couponSnapshot;
	private final LocalDateTime createdAt;


	// 생성자
	private Order(Long id, Long userId, String requestId, BigDecimal originalTotalPrice, BigDecimal discountAmount,
		BigDecimal totalPrice, OrderStatus status, List<OrderItem> items, CouponSnapshot couponSnapshot,
		LocalDateTime createdAt) {
		this.id = id;
		this.userId = userId;
		this.requestId = requestId;
		this.originalTotalPrice = originalTotalPrice;
		this.discountAmount = discountAmount;
		this.totalPrice = totalPrice;
		this.status = status;
		this.items = items;
		this.couponSnapshot = couponSnapshot;
		this.createdAt = createdAt;
	}


	/**
	 * 도메인 로직
	 * 1. 주문 생성
	 * 2. 주문 재생성 (Entity -> Model 매핑용도)
	 * 3. 주문 상태 변경
	 */

	// 1. 주문 생성 (쿠폰 적용 포함, 초기 상태: PENDING_PAYMENT)
	public static Order create(Long userId, String requestId, BigDecimal originalTotalPrice, BigDecimal discountAmount,
		List<OrderItem> items, CouponSnapshot couponSnapshot) {

		// userId null 검증
		Objects.requireNonNull(userId, "userId");

		// requestId 검증
		validateRequestId(requestId);

		// originalTotalPrice 검증
		validateTotalPrice(originalTotalPrice);

		// discountAmount null 방지
		BigDecimal resolvedDiscount = discountAmount != null ? discountAmount : BigDecimal.ZERO;

		// 최종 결제 금액 계산 (최소 0원)
		BigDecimal totalPrice = originalTotalPrice.subtract(resolvedDiscount).max(BigDecimal.ZERO);

		// totalPrice 검증
		validateTotalPrice(totalPrice);

		// items 검증
		validateItems(items);

		return new Order(null, userId, requestId, originalTotalPrice, resolvedDiscount, totalPrice,
			OrderStatus.PENDING_PAYMENT, items, couponSnapshot, null);
	}


	// 2. 주문 재생성 (Entity -> Model 매핑용도)
	public static Order reconstruct(Long id, Long userId, String requestId, BigDecimal originalTotalPrice, BigDecimal discountAmount,
		BigDecimal totalPrice, OrderStatus status, List<OrderItem> items, CouponSnapshot couponSnapshot, LocalDateTime createdAt) {
		return new Order(id, userId, requestId, originalTotalPrice, discountAmount, totalPrice, status, items, couponSnapshot, createdAt);
	}


	// 3. 주문 상태 변경 (허용된 전이만 가능)
	public void changeStatus(OrderStatus target) {

		// target null 검증
		Objects.requireNonNull(target, "target status");

		// 상태 전이 가능 여부 검증
		if (!this.status.canTransitionTo(target)) {
			throw new CoreException(ErrorType.ORDER_NOT_PAYABLE);
		}

		this.status = target;
	}


	/**
	 * private method
	 * - requestId 검증 (null 또는 공백 불가)
	 * - 총액 검증 (null 또는 음수 불가)
	 * - 주문 항목 검증
	 */

	// requestId 검증 (null 또는 공백 불가)
	private static void validateRequestId(String requestId) {
		if (requestId == null || requestId.isBlank()) {
			throw new CoreException(ErrorType.INVALID_REQUEST_ID);
		}
	}


	// 총액 검증 (null 또는 음수 불가)
	private static void validateTotalPrice(BigDecimal totalPrice) {
		if (totalPrice == null || totalPrice.compareTo(BigDecimal.ZERO) < 0) {
			throw new CoreException(ErrorType.INVALID_ORDER_TOTAL_PRICE);
		}
	}


	// 주문 항목 검증
	private static void validateItems(List<OrderItem> items) {
		if (items == null || items.isEmpty()) {
			throw new CoreException(ErrorType.ORDER_EMPTY_ITEMS);
		}
	}

}
