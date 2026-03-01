package com.loopers.catalog.brand.application.port.out.client.engagement;


public interface BrandLikeCleanupManager {

	/**
	 * 브랜드 좋아요 정리 포트
	 * 1. 브랜드 ID로 좋아요 전체 삭제
	 */

	// 1. 브랜드 ID로 좋아요 전체 삭제
	void deleteAllByBrandId(Long brandId);

}
