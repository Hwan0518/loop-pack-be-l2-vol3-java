package com.loopers.engagement.brandlike.interfaces.event;

import com.loopers.catalog.brand.domain.event.BrandDeletedEvent;
import com.loopers.engagement.brandlike.application.service.BrandLikeCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;


@Component
@RequiredArgsConstructor
public class BrandLikeCleanupEventListener {

	// service
	private final BrandLikeCommandService brandLikeCommandService;


	/**
	 * 브랜드 좋아요 정리 이벤트 리스너
	 * 1. 브랜드 삭제 시 관련 좋아요 전체 삭제
	 */

	// 1. 브랜드 삭제 시 관련 좋아요 전체 삭제
	@TransactionalEventListener
	public void handleBrandDeleted(BrandDeletedEvent event) {
		brandLikeCommandService.deleteAllByTargetId(event.brandId());
	}

}
