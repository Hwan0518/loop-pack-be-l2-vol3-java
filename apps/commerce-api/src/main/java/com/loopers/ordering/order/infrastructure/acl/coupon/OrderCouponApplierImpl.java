package com.loopers.ordering.order.infrastructure.acl.coupon;


import com.loopers.coupon.issuedcoupon.application.dto.out.CouponApplyResult;
import com.loopers.coupon.issuedcoupon.application.facade.IssuedCouponCommandFacade;
import com.loopers.ordering.order.application.port.out.client.coupon.OrderCouponApplier;
import com.loopers.ordering.order.application.port.out.client.coupon.OrderCouponApplyResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;


/**
 * ACL (Anti-Corruption Layer) - 주문 BC → 쿠폰 BC 통합 구현체 (쿠폰 적용)
 * 주문 확정 시 쿠폰 적용을 수행하는 역할 (Provider Facade에 위임)
 */
@Component
@RequiredArgsConstructor
public class OrderCouponApplierImpl implements OrderCouponApplier {

	// facade: 발급 쿠폰 명령 파사드 (coupon BC)
	private final IssuedCouponCommandFacade issuedCouponCommandFacade;


	/**
	 * 쿠폰 적용 (thin adapter — Provider Facade 위임 + Provider DTO → Consumer record 변환)
	 * 1. Provider Facade에 위임
	 * 2. Provider CouponApplyResult → Consumer OrderCouponApplyResult 변환
	 */

	// 1. 쿠폰 적용 위임 + Provider → Consumer 변환
	@Override
	public OrderCouponApplyResult apply(Long issuedCouponId, Long userId, BigDecimal totalPrice) {
		CouponApplyResult providerResult = issuedCouponCommandFacade.applyToCoupon(issuedCouponId, userId, totalPrice);

		// 2. Provider DTO → Consumer contract record 변환
		return new OrderCouponApplyResult(
			providerResult.issuedCouponId(),
			providerResult.discountAmount(),
			providerResult.couponSnapshotName(),
			providerResult.couponSnapshotType(),
			providerResult.couponSnapshotValue()
		);
	}

}
