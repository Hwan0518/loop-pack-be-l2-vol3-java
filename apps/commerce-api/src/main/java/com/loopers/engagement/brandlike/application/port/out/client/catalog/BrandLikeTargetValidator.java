package com.loopers.engagement.brandlike.application.port.out.client.catalog;


public interface BrandLikeTargetValidator {

	/**
	 * 브랜드 좋아요 대상 검증 포트
	 * - Cross-BC: engagement(brandlike) -> catalog(brand)
	 */

	// 1. 브랜드 존재 여부 검증 (미존재 시 Provider 예외 전파, 에러 매핑은 호출측 Service 책임)
	void validate(Long targetId);

}
