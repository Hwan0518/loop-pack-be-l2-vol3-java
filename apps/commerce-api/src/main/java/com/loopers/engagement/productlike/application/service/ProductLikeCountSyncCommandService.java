package com.loopers.engagement.productlike.application.service;


import com.loopers.engagement.productlike.application.port.out.client.catalog.ProductLikeCountSyncer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


/**
 * 상품 좋아요 수 동기화 서비스
 * 좋아요 생성/삭제 시 상품의 좋아요 수를 동기화하는 부수효과를 담당한다.
 */
@Service
@RequiredArgsConstructor
public class ProductLikeCountSyncCommandService {

	// port
	private final ProductLikeCountSyncer productLikeCountSyncer;


	/**
	 * 상품 좋아요 수 동기화
	 * 1. 좋아요 수 증가
	 * 2. 좋아요 수 감소
	 */

	// 1. 좋아요 수 증가
	@Transactional
	public void increaseLikeCount(Long productId) {
		productLikeCountSyncer.increaseLikeCount(productId);
	}


	// 2. 좋아요 수 감소
	@Transactional
	public void decreaseLikeCount(Long productId) {
		productLikeCountSyncer.decreaseLikeCount(productId);
	}

}
