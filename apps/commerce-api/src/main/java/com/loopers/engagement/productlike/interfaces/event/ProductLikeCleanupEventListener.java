package com.loopers.engagement.productlike.interfaces.event;

import com.loopers.catalog.product.domain.event.ProductDeletedEvent;
import com.loopers.engagement.productlike.application.service.ProductLikeCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;


@Component
@RequiredArgsConstructor
public class ProductLikeCleanupEventListener {

	// service
	private final ProductLikeCommandService productLikeCommandService;


	/**
	 * 상품 좋아요 정리 이벤트 리스너
	 * 1. 상품 삭제 시 관련 좋아요 전체 삭제
	 */

	// 1. 상품 삭제 시 관련 좋아요 전체 삭제
	@TransactionalEventListener
	public void handleProductDeleted(ProductDeletedEvent event) {
		productLikeCommandService.deleteAllByTargetId(event.productId());
	}

}
