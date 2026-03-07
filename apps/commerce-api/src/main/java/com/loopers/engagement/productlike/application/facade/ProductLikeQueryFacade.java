package com.loopers.engagement.productlike.application.facade;

import com.loopers.engagement.productlike.application.dto.out.ProductLikePageOutDto;
import com.loopers.engagement.productlike.application.service.ProductLikeQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class ProductLikeQueryFacade {

	// service
	private final ProductLikeQueryService productLikeQueryService;


	/**
	 * 상품 좋아요 조회 퍼사드
	 * 1. 사용자의 상품 좋아요 목록 조회
	 * 2. 사용자의 상품 좋아요 여부 확인
	 */

	// 1. 사용자의 상품 좋아요 목록 조회
	@Transactional(readOnly = true)
	public ProductLikePageOutDto getLikesByUserId(Long userId, int page, int size) {

		return productLikeQueryService.getLikesByUserId(userId, page, size);
	}


	// 2. 사용자의 상품 좋아요 여부 확인
	@Transactional(readOnly = true)
	public boolean isLikedByUser(Long userId, Long targetId) {

		return productLikeQueryService.isLikedByUser(userId, targetId);
	}

}
