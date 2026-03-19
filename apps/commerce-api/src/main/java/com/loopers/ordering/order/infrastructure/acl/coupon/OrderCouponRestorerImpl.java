package com.loopers.ordering.order.infrastructure.acl.coupon;


import com.loopers.coupon.issuedcoupon.application.facade.IssuedCouponCommandFacade;
import com.loopers.ordering.order.application.port.out.client.coupon.OrderCouponRestorer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;


/**
 * ACL (Anti-Corruption Layer) - 주문 BC → 쿠폰 BC 통합 구현체 (쿠폰 복원)
 * 주문 만료 시 사용된 쿠폰을 복원하는 역할 (Provider Facade에 위임)
 */
@Component
@RequiredArgsConstructor
public class OrderCouponRestorerImpl implements OrderCouponRestorer {

	// facade: 발급 쿠폰 명령 파사드 (coupon BC)
	private final IssuedCouponCommandFacade issuedCouponCommandFacade;


	/**
	 * 쿠폰 복원
	 * 1. Provider Facade에 위임 (USED → AVAILABLE 상태 변경)
	 */

	// 1. 쿠폰 복원 — Provider Facade에 위임
	@Override
	public void restoreCoupon(Long issuedCouponId) {
		issuedCouponCommandFacade.restoreCoupon(issuedCouponId);
	}

}
