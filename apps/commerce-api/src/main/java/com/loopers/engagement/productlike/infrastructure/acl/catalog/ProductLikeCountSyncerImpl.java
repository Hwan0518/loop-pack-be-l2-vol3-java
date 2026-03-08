package com.loopers.engagement.productlike.infrastructure.acl.catalog;


import com.loopers.catalog.product.application.facade.ProductCommandFacade;
import com.loopers.engagement.productlike.application.port.out.client.catalog.ProductLikeCountSyncer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;


/**
 * ACL (Anti-Corruption Layer) - 참여 BC → 카탈로그 BC 통합 구현체 (좋아요 수 동기화)
 * 상품 좋아요 생성/삭제 시 상품의 좋아요 수를 동기화하는 역할 (Provider Facade에 위임)
 */
@Component
@RequiredArgsConstructor
public class ProductLikeCountSyncerImpl implements ProductLikeCountSyncer {

	// facade: 상품 명령 파사드 (catalog BC)
	private final ProductCommandFacade productCommandFacade;


	/**
	 * 상품 좋아요 수 동기화
	 * 1. 좋아요 수 증가 — Provider Facade에 위임
	 * 2. 좋아요 수 감소 — Provider Facade에 위임
	 */

	// 1. 좋아요 수 증가 — Provider Facade에 위임
	@Override
	public void increaseLikeCount(Long productId) {
		productCommandFacade.increaseLikeCount(productId);
	}


	// 2. 좋아요 수 감소 — Provider Facade에 위임
	@Override
	public void decreaseLikeCount(Long productId) {
		productCommandFacade.decreaseLikeCount(productId);
	}

}
