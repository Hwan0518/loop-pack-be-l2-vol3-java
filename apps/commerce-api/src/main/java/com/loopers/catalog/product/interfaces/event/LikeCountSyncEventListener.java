package com.loopers.catalog.product.interfaces.event;

import com.loopers.catalog.product.application.facade.ProductCommandFacade;
import com.loopers.engagement.productlike.domain.event.ProductLikeCancelledEvent;
import com.loopers.engagement.productlike.domain.event.ProductLikeCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;


@Component
@RequiredArgsConstructor
public class LikeCountSyncEventListener {

	// facade
	private final ProductCommandFacade productCommandFacade;


	/**
	 * 좋아요 수 동기화 이벤트 리스너
	 * 1. 상품 좋아요 생성 시 좋아요 수 증가
	 * 2. 상품 좋아요 취소 시 좋아요 수 감소
	 */

	// 1. 상품 좋아요 생성 시 좋아요 수 증가
	@TransactionalEventListener
	public void handleProductLikeCreated(ProductLikeCreatedEvent event) {
		productCommandFacade.increaseLikeCount(event.productId());
	}


	// 2. 상품 좋아요 취소 시 좋아요 수 감소
	@TransactionalEventListener
	public void handleProductLikeCancelled(ProductLikeCancelledEvent event) {
		productCommandFacade.decreaseLikeCount(event.productId());
	}

}
