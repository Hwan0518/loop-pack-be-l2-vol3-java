package com.loopers.catalog.brand.infrastructure.acl.engagement;


import com.loopers.catalog.brand.application.port.out.client.engagement.BrandLikeCleanupManager;
import com.loopers.engagement.brandlike.application.facade.BrandLikeCommandFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;


/**
 * ACL (Anti-Corruption Layer) - 카탈로그 BC → 참여 BC 통합 구현체 (브랜드 좋아요 정리)
 * 브랜드 삭제 시 관련 좋아요 데이터를 정리하는 역할 (Provider Facade에 위임)
 */
@Component
@RequiredArgsConstructor
public class BrandLikeCleanupManagerImpl implements BrandLikeCleanupManager {

	// facade: 브랜드 좋아요 명령 파사드 (engagement BC)
	private final BrandLikeCommandFacade brandLikeCommandFacade;


	/**
	 * 브랜드 좋아요 정리
	 * 1. 브랜드 ID로 좋아요 전체 삭제 — Provider Facade에 위임
	 */

	// 1. 브랜드 ID로 좋아요 전체 삭제 — Provider Facade에 위임
	@Override
	public void deleteAllByBrandId(Long brandId) {
		brandLikeCommandFacade.deleteAllByBrandId(brandId);
	}

}
