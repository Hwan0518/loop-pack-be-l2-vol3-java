package com.loopers.engagement.productlike.application.facade;

import com.loopers.engagement.productlike.application.dto.out.ProductLikeOutDto;
import com.loopers.engagement.productlike.application.service.ProductLikeCommandService;
import com.loopers.engagement.productlike.domain.model.ProductLike;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class ProductLikeCommandFacade {

	// service
	private final ProductLikeCommandService productLikeCommandService;


	/**
	 * 상품 좋아요 명령 퍼사드
	 * 1. 상품 좋아요 생성
	 * 2. 상품 좋아요 삭제
	 */

	// 1. 상품 좋아요 생성
	@Transactional
	public ProductLikeOutDto createLike(String loginId, String password, Long targetId) {

		// 사용자 인증
		Long userId = productLikeCommandService.authenticate(loginId, password);

		// 좋아요 생성
		ProductLike productLike = productLikeCommandService.createLike(userId, targetId);

		// DTO 변환
		return ProductLikeOutDto.from(productLike);
	}


	// 2. 상품 좋아요 삭제
	@Transactional
	public void deleteLike(String loginId, String password, Long targetId) {

		// 사용자 인증
		Long userId = productLikeCommandService.authenticate(loginId, password);

		// 좋아요 삭제
		productLikeCommandService.deleteLike(userId, targetId);
	}

}
