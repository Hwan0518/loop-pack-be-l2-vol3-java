package com.loopers.engagement.productlike.application.port.out.client.catalog;


public interface ProductLikeTargetValidator {

	/**
	 * 상품 좋아요 대상 검증 포트
	 * - Cross-BC: engagement(productlike) -> catalog(product)
	 */

	// 1. 상품 존재 여부 검증 (미존재 시 Provider 예외 전파, 에러 매핑은 호출측 Service 책임)
	void validate(Long targetId);

}
