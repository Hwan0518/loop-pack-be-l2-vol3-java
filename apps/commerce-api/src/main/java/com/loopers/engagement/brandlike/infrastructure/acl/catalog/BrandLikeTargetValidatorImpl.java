package com.loopers.engagement.brandlike.infrastructure.acl.catalog;


import com.loopers.catalog.brand.application.facade.BrandQueryFacade;
import com.loopers.engagement.brandlike.application.port.out.client.catalog.BrandLikeTargetValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;


/**
 * ACL (Anti-Corruption Layer) - 브랜드좋아요 BC → 카탈로그 BC 통합 구현체 (대상 검증)
 * 좋아요 대상 브랜드의 존재 여부를 검증하는 역할 (Provider Facade에 단순 위임)
 */
@Component
@RequiredArgsConstructor
public class BrandLikeTargetValidatorImpl implements BrandLikeTargetValidator {

	// facade: 브랜드 조회 파사드 (catalog BC)
	private final BrandQueryFacade brandQueryFacade;


	/**
	 * 브랜드 존재 여부 검증 (VISIBLE 상태만)
	 * 1. Provider Facade를 통해 VISIBLE 브랜드 존재 검증 (단순 위임, 에러 매핑은 호출측 Service 책임)
	 */

	// 1. 브랜드 존재 여부 검증 — Provider Facade 단순 위임
	@Override
	public void validate(Long targetId) {
		brandQueryFacade.validateVisibleBrandExists(targetId);
	}

}
