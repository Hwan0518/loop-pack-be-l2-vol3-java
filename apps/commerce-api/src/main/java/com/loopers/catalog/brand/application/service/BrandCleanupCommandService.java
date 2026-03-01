package com.loopers.catalog.brand.application.service;


import com.loopers.catalog.brand.application.port.out.client.engagement.BrandLikeCleanupManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


/**
 * 브랜드 부수효과 정리 서비스
 * 브랜드 삭제 시 관련 Cross-BC 데이터 정리를 담당한다.
 */
@Service
@RequiredArgsConstructor
public class BrandCleanupCommandService {

	// port
	private final BrandLikeCleanupManager brandLikeCleanupManager;


	/**
	 * 브랜드 부수효과 정리
	 * 1. 브랜드 좋아요 전체 삭제
	 */

	// 1. 브랜드 좋아요 전체 삭제
	@Transactional
	public void deleteAllBrandLikes(Long brandId) {
		brandLikeCleanupManager.deleteAllByBrandId(brandId);
	}

}
