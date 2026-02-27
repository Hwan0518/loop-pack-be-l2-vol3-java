package com.loopers.catalog.product.infrastructure.acl.cart;


import com.loopers.cart.cart.application.facade.CartItemCommandFacade;
import com.loopers.catalog.product.application.port.out.client.cart.CartItemCleanupManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;


/**
 * ACL (Anti-Corruption Layer) - 카탈로그 BC → 장바구니 BC 통합 구현체 (장바구니 항목 정리)
 * 상품 삭제 시 관련 장바구니 항목을 정리하는 역할 (Provider Facade에 위임)
 */
@Component
@RequiredArgsConstructor
public class CartItemCleanupManagerImpl implements CartItemCleanupManager {

	// facade: 장바구니 항목 명령 파사드 (cart BC)
	private final CartItemCommandFacade cartItemCommandFacade;


	/**
	 * 장바구니 항목 정리
	 * 1. 상품 ID로 장바구니 항목 전체 삭제 — Provider Facade에 위임
	 */

	// 1. 상품 ID로 장바구니 항목 전체 삭제 — Provider Facade에 위임
	@Override
	public void deleteAllByProductId(Long productId) {
		cartItemCommandFacade.deleteAllByProductId(productId);
	}

}
