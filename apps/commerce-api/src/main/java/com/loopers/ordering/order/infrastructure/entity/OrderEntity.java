package com.loopers.ordering.order.infrastructure.entity;


import com.loopers.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;


/**
 * 주문 엔티티
 * - userId: 주문자 ID
 * - requestId: 멱등성 보장용 요청 ID
 * - originalTotalPrice: 쿠폰 적용 전 총액
 * - discountAmount: 할인 금액 (0 = 쿠폰 미사용)
 * - totalPrice: 최종 결제 금액
 * - issuedCouponId: 적용된 발급 쿠폰 ID (nullable)
 * - couponSnapshotName: 쿠폰 이름 스냅샷 (nullable)
 * - couponSnapshotType: 쿠폰 타입 스냅샷 (nullable)
 * - couponSnapshotValue: 쿠폰 할인 값 스냅샷 (nullable)
 */
@Entity
@Table(name = "orders",
	uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "request_id"}),
	indexes = {
		// 주문 내역 조회: WHERE user_id = ? ORDER BY created_at DESC / 기간 필터
		@Index(name = "idx_orders_user_created", columnList = "user_id, created_at")
	}
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderEntity extends BaseEntity {

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "request_id", nullable = false)
	private String requestId;

	@Column(name = "original_total_price", nullable = false, precision = 15, scale = 2)
	private BigDecimal originalTotalPrice;

	@Column(name = "discount_amount", nullable = false, precision = 15, scale = 2)
	private BigDecimal discountAmount;

	@Column(name = "total_price", nullable = false, precision = 15, scale = 2)
	private BigDecimal totalPrice;

	@Column(name = "issued_coupon_id")
	private Long issuedCouponId;

	@Column(name = "coupon_snapshot_name", length = 100)
	private String couponSnapshotName;

	@Column(name = "coupon_snapshot_type", length = 10)
	private String couponSnapshotType;

	@Column(name = "coupon_snapshot_value", precision = 15, scale = 2)
	private BigDecimal couponSnapshotValue;


	private OrderEntity(Long userId, String requestId, BigDecimal originalTotalPrice, BigDecimal discountAmount,
		BigDecimal totalPrice, Long issuedCouponId, String couponSnapshotName,
		String couponSnapshotType, BigDecimal couponSnapshotValue) {
		this.userId = userId;
		this.requestId = requestId;
		this.originalTotalPrice = originalTotalPrice;
		this.discountAmount = discountAmount;
		this.totalPrice = totalPrice;
		this.issuedCouponId = issuedCouponId;
		this.couponSnapshotName = couponSnapshotName;
		this.couponSnapshotType = couponSnapshotType;
		this.couponSnapshotValue = couponSnapshotValue;
	}


	public static OrderEntity of(Long userId, String requestId, BigDecimal originalTotalPrice, BigDecimal discountAmount,
		BigDecimal totalPrice, Long issuedCouponId, String couponSnapshotName,
		String couponSnapshotType, BigDecimal couponSnapshotValue) {
		return new OrderEntity(userId, requestId, originalTotalPrice, discountAmount, totalPrice,
			issuedCouponId, couponSnapshotName, couponSnapshotType, couponSnapshotValue);
	}

}
