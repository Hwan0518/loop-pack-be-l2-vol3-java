package com.loopers.catalog.product.infrastructure.acl.engagement;


import com.loopers.catalog.product.application.port.out.client.engagement.ProductLikeCleanupManager;
import com.loopers.engagement.productlike.application.facade.ProductLikeCommandFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;


/**
 * ACL (Anti-Corruption Layer) - 카탈로그 BC → 참여 BC 통합 구현체 (상품 좋아요 정리)
 * 상품 삭제 시 관련 좋아요 데이터를 정리하는 역할 (Provider Facade에 위임)
 */
@Component
@RequiredArgsConstructor
public class ProductLikeCleanupManagerImpl implements ProductLikeCleanupManager {

	// facade: 상품 좋아요 명령 파사드 (engagement BC)
	private final ProductLikeCommandFacade productLikeCommandFacade;


	/**
	 * 상품 좋아요 정리
	 * 1. 상품 ID로 좋아요 전체 삭제 — Provider Facade에 위임
	 */

	// 1. 상품 ID로 좋아요 전체 삭제 — Provider Facade에 위임
	@Override
	public void deleteAllByProductId(Long productId) {
		productLikeCommandFacade.deleteAllByProductId(productId);
	}

}
