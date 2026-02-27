package com.loopers.ordering.order.infrastructure.acl.cart;


import com.loopers.cart.cart.application.facade.CartItemCommandFacade;
import com.loopers.ordering.order.application.port.out.client.cart.OrderCartItemCleaner;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;


/**
 * ACL (Anti-Corruption Layer) - 주문 BC → 장바구니 BC 통합 구현체 (장바구니 항목 정리)
 * 주문 생성 시 주문된 장바구니 항목을 정리하는 역할 (Provider Facade에 위임)
 */
@Component
@RequiredArgsConstructor
public class OrderCartItemCleanerImpl implements OrderCartItemCleaner {

	// facade: 장바구니 항목 명령 파사드 (cart BC)
	private final CartItemCommandFacade cartItemCommandFacade;


	/**
	 * 장바구니 항목 정리
	 * 1. 사용자 ID와 장바구니 항목 ID 목록으로 삭제 — Provider Facade에 위임
	 */

	// 1. 사용자 ID와 장바구니 항목 ID 목록으로 삭제 — Provider Facade에 위임
	@Override
	public void deleteCartItems(Long userId, List<Long> cartItemIds) {
		cartItemCommandFacade.deleteAllByUserIdAndIds(userId, cartItemIds);
	}

}
