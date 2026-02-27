package com.loopers.engagement.productlike.infrastructure.acl.catalog;


import com.loopers.catalog.product.application.facade.ProductCommandFacade;
import com.loopers.engagement.productlike.application.port.out.client.catalog.ProductLikeCountSyncer;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;


/**
 * ACL (Anti-Corruption Layer) - 참여 BC → 카탈로그 BC 통합 구현체 (좋아요 수 동기화)
 * 상품 좋아요 생성/삭제 시 상품의 좋아요 수를 동기화하는 역할 (Provider Facade에 위임)
 *
 * <p>@Lazy: catalog ↔ engagement 양방향 동기 의존으로 인한 순환 참조 방지
 * <ul>
 *   <li>ProductCommandFacade → ProductCleanupCommandService → ProductLikeCleanupManagerImpl
 *       → ProductLikeCommandFacade → ProductLikeCountSyncCommandService → (here) → ProductCommandFacade</li>
 * </ul>
 *
 * <p>TODO: 좋아요 수 동기화를 비동기 이벤트(Kafka 등)로 전환하면 @Lazy 제거 가능
 */
@Component
public class ProductLikeCountSyncerImpl implements ProductLikeCountSyncer {

	// facade: 상품 명령 파사드 (catalog BC)
	private final ProductCommandFacade productCommandFacade;

	public ProductLikeCountSyncerImpl(@Lazy ProductCommandFacade productCommandFacade) {
		this.productCommandFacade = productCommandFacade;
	}


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
