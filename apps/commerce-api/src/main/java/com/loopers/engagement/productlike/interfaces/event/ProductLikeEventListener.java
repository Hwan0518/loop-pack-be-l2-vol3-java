package com.loopers.engagement.productlike.interfaces.event;


import com.loopers.engagement.productlike.application.service.ProductLikeCommandService;
import com.loopers.engagement.productlike.domain.event.ProductLikedEvent;
import com.loopers.engagement.productlike.domain.event.ProductUnlikedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;


/**
 * 상품 좋아요 이벤트 리스너
 * 1. likeCount 증가 (eventual consistency)
 * 2. likeCount 감소 (eventual consistency)
 */

@Component
@RequiredArgsConstructor
@Slf4j
public class ProductLikeEventListener {

	// service
	private final ProductLikeCommandService productLikeCommandService;


	// 1. likeCount 증가 (eventual consistency)
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	@Async
	public void handleLikeCountIncrease(ProductLikedEvent event) {
		try {
			productLikeCommandService.increaseLikeCount(event.productId());
		} catch (Exception e) {
			log.warn("[ProductLikeEvent] likeCount 증가 실패 productId={}", event.productId(), e);
		}
	}


	// 2. likeCount 감소 (eventual consistency)
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	@Async
	public void handleLikeCountDecrease(ProductUnlikedEvent event) {
		try {
			productLikeCommandService.decreaseLikeCount(event.productId());
		} catch (Exception e) {
			log.warn("[ProductLikeEvent] likeCount 감소 실패 productId={}", event.productId(), e);
		}
	}

}
