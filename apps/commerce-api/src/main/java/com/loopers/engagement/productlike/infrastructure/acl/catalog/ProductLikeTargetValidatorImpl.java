package com.loopers.engagement.productlike.infrastructure.acl.catalog;


import com.loopers.catalog.product.application.facade.ProductQueryFacade;
import com.loopers.engagement.productlike.application.port.out.client.catalog.ProductLikeTargetValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;


/**
 * ACL (Anti-Corruption Layer) - 상품좋아요 BC → 카탈로그 BC 통합 구현체 (대상 검증)
 * 좋아요 대상 상품의 존재 여부를 검증하는 역할 (Provider Facade에 단순 위임)
 */
@Component
@RequiredArgsConstructor
public class ProductLikeTargetValidatorImpl implements ProductLikeTargetValidator {

	// facade: 상품 조회 파사드 (catalog BC)
	private final ProductQueryFacade productQueryFacade;


	/**
	 * 상품 존재 여부 검증
	 * 1. Provider Facade를 통해 활성 상품 조회 (단순 위임, 에러 매핑은 호출측 Service 책임)
	 */

	// 1. 상품 존재 여부 검증 — Provider Facade 단순 위임
	@Override
	public void validate(Long targetId) {
		productQueryFacade.findActiveById(targetId);
	}

}
