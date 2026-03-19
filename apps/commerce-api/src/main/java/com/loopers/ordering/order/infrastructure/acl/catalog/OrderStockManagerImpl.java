package com.loopers.ordering.order.infrastructure.acl.catalog;


import com.loopers.catalog.product.application.facade.ProductCommandFacade;
import com.loopers.ordering.order.application.port.out.client.catalog.OrderStockManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;


/**
 * ACL (Anti-Corruption Layer) - 주문 BC → 카탈로그 BC 통합 구현체 (재고 관리)
 * 주문 확정 시 상품 재고를 차감하는 역할 (Provider Facade에 위임)
 */
@Component
@RequiredArgsConstructor
public class OrderStockManagerImpl implements OrderStockManager {

	// facade: 상품 명령 파사드 (catalog BC)
	private final ProductCommandFacade productCommandFacade;


	/**
	 * 상품 재고 관리
	 * 1. 재고 차감 — Provider Facade에 위임
	 * 2. 재고 복원 — Provider Facade에 위임 (보상 트랜잭션)
	 */

	// 1. 상품 재고 차감 — Provider Facade에 위임
	@Override
	public void decreaseStock(Long productId, Long quantity) {
		productCommandFacade.decreaseStock(productId, quantity);
	}


	// 2. 상품 재고 복원 — Provider Facade에 위임 (보상 트랜잭션)
	@Override
	public void restoreStock(Long productId, Long quantity) {
		productCommandFacade.increaseStock(productId, quantity);
	}

}
