package com.loopers.cart.cart.interfaces.event;


import com.loopers.cart.cart.application.service.CartItemCommandService;
import com.loopers.catalog.product.domain.event.ProductDeletedEvent;
import com.loopers.ordering.order.domain.event.OrderCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;


@Component
@RequiredArgsConstructor
public class CartCleanupEventListener {

	// service
	private final CartItemCommandService cartItemCommandService;


	/**
	 * 장바구니 정리 이벤트 리스너
	 * 1. 상품 삭제 시 장바구니 항목 정리
	 * 2. 주문 생성 시 장바구니 항목 정리
	 */

	// 1. 상품 삭제 시 장바구니 항목 정리
	@TransactionalEventListener
	public void handleProductDeleted(ProductDeletedEvent event) {
		cartItemCommandService.deleteAllByProductId(event.productId());
	}


	// 2. 주문 생성 시 장바구니 항목 정리
	@TransactionalEventListener
	public void handleOrderCreated(OrderCreatedEvent event) {
		cartItemCommandService.deleteAllByUserIdAndIds(event.userId(), event.cartItemIds());
	}

}
