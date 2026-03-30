package com.loopers.ordering.order.application.port.out.client.coupon;


import java.math.BigDecimal;


/**
 * 쿠폰 적용 결과 — ordering BC 소유 contract record
 * - Consumer BC(ordering)가 소유하며, ACL에서 Provider(coupon) DTO를 변환하여 생성
 * - ordering BC 내부에서만 사용
 */
public record OrderCouponApplyResult(
	Long issuedCouponId,
	BigDecimal discountAmount,
	String couponSnapshotName,
	String couponSnapshotType,
	BigDecimal couponSnapshotValue
) {

	// 쿠폰 미사용 시 기본값 반환
	public static OrderCouponApplyResult noDiscount() {
		return new OrderCouponApplyResult(null, BigDecimal.ZERO, null, null, null);
	}

	// 할인 적용 여부 확인
	public boolean hasDiscount() {
		return issuedCouponId != null;
	}

}
