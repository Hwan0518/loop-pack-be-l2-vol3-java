package com.loopers.catalog.product.application.facade;


import com.loopers.catalog.product.application.service.ProductCommandService;
import com.loopers.catalog.product.application.service.ProductQueryService;
import com.loopers.catalog.product.domain.model.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class ProductLikeCountCommandFacade {

	// service
	private final ProductCommandService productCommandService;
	private final ProductQueryService productQueryService;


	/**
	 * 상품 좋아요 수 명령 파사드
	 * 1. 좋아요 수 증가
	 * 2. 좋아요 수 감소
	 */

	// 1. 좋아요 수 증가
	@Transactional
	public void increaseLikeCount(Long productId) {

		// 활성 상품 조회
		Product product = productQueryService.findActiveById(productId);

		// 좋아요 수 증가
		productCommandService.increaseLikeCount(product);
	}


	// 2. 좋아요 수 감소
	@Transactional
	public void decreaseLikeCount(Long productId) {

		// 활성 상품 조회
		Product product = productQueryService.findActiveById(productId);

		// 좋아요 수 감소
		productCommandService.decreaseLikeCount(product);
	}

}
