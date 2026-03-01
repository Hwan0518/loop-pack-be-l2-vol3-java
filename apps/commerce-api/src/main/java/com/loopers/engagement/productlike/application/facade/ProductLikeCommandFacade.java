package com.loopers.engagement.productlike.application.facade;

import com.loopers.engagement.productlike.application.dto.out.ProductLikeOutDto;
import com.loopers.engagement.productlike.application.service.ProductLikeCommandService;
import com.loopers.engagement.productlike.domain.model.ProductLike;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;


@Service
@RequiredArgsConstructor
public class ProductLikeCommandFacade {

	// service
	private final ProductLikeCommandService productLikeCommandService;


	/**
	 * 상품 좋아요 명령 퍼사드
	 * 1. 상품 좋아요 생성 (멱등)
	 * 2. 상품 좋아요 삭제
	 * 3. 상품 ID로 상품 좋아요 전체 삭제 (Cross-BC 전용 — ACL에서 호출)
	 */

	// 1. 상품 좋아요 생성 (멱등)
	@Transactional
	public ProductLikeOutDto createLike(String loginId, String password, Long targetId) {

		// 사용자 인증
		Long userId = productLikeCommandService.authenticate(loginId, password);

		// 기존 좋아요 존재 시 기존 반환 (멱등)
		Optional<ProductLike> existing = productLikeCommandService.findLike(userId, targetId);
		if (existing.isPresent()) {
			return ProductLikeOutDto.from(existing.get());
		}

		// 좋아요 생성
		ProductLike productLike = productLikeCommandService.createLike(userId, targetId);

		// 좋아요 수 증가 (Cross-BC 부수효과)
		productLikeCommandService.increaseLikeCount(targetId);

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

		// 좋아요 수 감소 (Cross-BC 부수효과)
		productLikeCommandService.decreaseLikeCount(targetId);
	}


	// 3. 상품 ID로 상품 좋아요 전체 삭제 (Cross-BC 전용 — ACL에서 호출)
	@Transactional
	public void deleteAllByProductId(Long productId) {
		productLikeCommandService.deleteAllByTargetId(productId);
	}

}
