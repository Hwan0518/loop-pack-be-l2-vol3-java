package com.loopers.coupon.issuedcoupon.application.dto.out;


import java.math.BigDecimal;


/**
 * 쿠폰 적용 결과 (Cross-BC 데이터 전달 record)
 * - ordering BC에서 쿠폰 적용 결과를 수신할 때 사용
 */
public record CouponApplyResult(
	Long issuedCouponId,
	BigDecimal discountAmount,
	String couponSnapshotName,
	String couponSnapshotType,
	BigDecimal couponSnapshotValue
) {

	// 쿠폰 미사용 시 기본값 반환
	public static CouponApplyResult noDiscount() {
		return new CouponApplyResult(null, BigDecimal.ZERO, null, null, null);
	}

	// 할인 적용 여부 확인
	public boolean hasDiscount() {
		return issuedCouponId != null;
	}

}
